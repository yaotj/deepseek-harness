package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.notify.AppNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import static com.chinasofti.huateng.model.enums.CardTypeCodeEnum.isHceCard;

@Component
class GateTicketHandler {

    private static final Logger log = LoggerFactory.getLogger(GateTicketHandler.class);
    private static final String CHANNEL_TYPE_BLUETOOTH = "01";

    @Autowired
    private QRCodeStatusStore qrCodeStatusStore;

    @Autowired
    private GateTicketWriter gateTicketWriter;

    @Autowired
    private GateCardTypeEnricher cardTypeEnricher;

    @Autowired
    private GateTxnAssembler txnAssembler;

    @Autowired
    private GateDailyTicketCoordinator dailyTicketCoordinator;

    @Autowired
    private GateResponseAssembler responseAssembler;

    @Autowired
    private AppNotifyService appNotifyService;

    @Autowired
    private GateFarePaymentOrchestrator farePaymentOrchestrator;


    public void handleNotifyVerifyResult(NotifyVerifyResultReqDTO request, String cardId,
                                        NotifyVerifyResultRespDTO response) {
        log.info("IF1A-01 ticket-server 收到闸机检票通知, 请求参数={}", request);

        QRCodeStatus currentStatus = qrCodeStatusStore.findByCardId(cardId);
        log.info("IF1A-01 当前票卡状态, 来自数据库={}", currentStatus);
        if (currentStatus == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return;
        }

        warnIfStaleNotify(request, currentStatus);

        handleGateTransaction(request, currentStatus, response);
    }


    private void warnIfStaleNotify(NotifyVerifyResultReqDTO request, QRCodeStatus currentStatus) {
        String handleDateTime = request.getHandleDateTime();
        String lastTxnTime = currentStatus.getLastTxnTime();
        if (StringUtils.hasText(handleDateTime) && StringUtils.hasText(lastTxnTime)
                && handleDateTime.compareTo(lastTxnTime) < 0) {
            log.warn("IF1A-01 通知乱序/迟到（仅观测、状态照常推进），cardId={}, trxType={}, deviceId={}, handleDateTime={} < 库内lastTxnTime={}, 当前codeStatus={}",
                    request.getCardId(), request.getTrxType(), request.getDeviceId(),
                    handleDateTime, lastTxnTime, currentStatus.getCodeStatus());
        }
    }


    private void handleGateTransaction(NotifyVerifyResultReqDTO request, QRCodeStatus currentStatus,
                                       NotifyVerifyResultRespDTO response) {
        String gateCardType = request.getCardType();
        cardTypeEnricher.applyActualCardType(request, response);
        String resolvedCardType = CardTypeMapping.toIssueCardType(request.getCardType());
        log.info("IF1A-01 卡种判定完成, cardId={}, 闸机上送cardType={}, 富化后cardType={}, resolvedCardType={}, signChannelCode={}",
                request.getCardId(), gateCardType, request.getCardType(), resolvedCardType,
                request.getSignChannelCode());

        // 后付费卡种必须已签约，未签约拒绝过闸
        if (cardTypeEnricher.rejectUnsignedPostpaid(request, response)) {
            log.warn("IF1A-01 后付费卡种未签约，拒绝过闸, cardId={}, resolvedCardType={}",
                    request.getCardId(), resolvedCardType);
            return;
        }

        if (!dailyTicketCoordinator.checkEntryAllowed(request, response, resolvedCardType)) {
            return;
        }

        request.setLastTicketStatus(currentStatus.getCodeStatus());
        request.setLastHandleStationCode(currentStatus.getLastTxnStation());
        if (!StringUtils.hasText(request.getLastHandleDateTime())) {
            request.setLastHandleDateTime(currentStatus.getLastTxnTime());
        }

        QRCodeTxnDetail detail = txnAssembler.buildTxnDetail(request);
        QRCodeStatus nextStatus = txnAssembler.buildNextStatus(request, currentStatus);
        log.info("IF1A-01 写入交易明细, detail = {}", detail);
        log.info("IF1A-01 更新票卡状态明细, detail = {}", nextStatus);

        GateTicketWriter.WriteResult writeResult = writeAndAdvance(request, detail, nextStatus, currentStatus);
        QRCodeStatus effectiveStatus = writeResult.status();

        if (!writeResult.duplicate()) {
            dailyTicketCoordinator.markUsedOnExit(request, resolvedCardType);

            pushDownstream(request, effectiveStatus, gateCardType, resolvedCardType);
        }

        responseAssembler.fillSuccessResponse(request, response, effectiveStatus, resolvedCardType);

        farePaymentOrchestrator.settleIfNeeded(request, response);
    }

    private GateTicketWriter.WriteResult writeAndAdvance(NotifyVerifyResultReqDTO request, QRCodeTxnDetail detail,
                                                        QRCodeStatus nextStatus, QRCodeStatus currentStatus) {
        GateTicketWriter.WriteResult writeResult = gateTicketWriter.saveTxnAndAdvanceStatus(
                detail, nextStatus, currentStatus.getTxnSeq());
        QRCodeStatus effectiveStatus = writeResult.status();
        if (writeResult.duplicate()) {
            log.info("IF1A-01 闸机交易明细重复上送, 状态不再推进、扣次与出向通知一律不重跑, cardId={}, trxType={}, handleDateTime={}, ticketTransSeq={}, deviceId={}, 库内useCount={}, 库内txnSeq={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(),
                    request.getTicketTransSeq(), request.getDeviceId(),
                    effectiveStatus.getUseCount(), effectiveStatus.getTxnSeq());
        }
        log.info("IF1A-01 更新票卡状态完成, cardId={}, isDuplicate={}, nextStatusCode={}, updateCount={}",
                request.getCardId(), writeResult.duplicate(), effectiveStatus.getCodeStatus(),
                writeResult.updateCount());
        return writeResult;
    }

    private void pushDownstream(NotifyVerifyResultReqDTO request, QRCodeStatus effectiveStatus,
                                String gateCardType, String resolvedCardType) {
        String cardType = request.getCardType();
        if (isHceCard(cardType)) {
            log.info("IF1A-01 HCE卡数据回写, cardId={}, cardType={}", request.getCardId(), cardType);
            cardTypeEnricher.updateHceDataFromGateTransaction(request);
            return;
        }
        appNotifyService.notifyVerifyResult(request, effectiveStatus, gateCardType);

        // R6 卡片行程通知（2026-11-17 APP 侧更正）：蓝牙 channelType=01 即 HCE，不推；其余卡型进出站/补站都推。
        if (!CHANNEL_TYPE_BLUETOOTH.equals(request.getChannelType())) {
            appNotifyService.notifyCardTran(request, effectiveStatus,
                    dailyTicketCoordinator.resolveCountingFlag(resolvedCardType));
        } else {
            log.info("IF1A-01 蓝牙渠道不推卡片行程通知, cardId={}, cardType={}, channelType={}",
                    request.getCardId(), cardType, request.getChannelType());
        }

        if (IssueChannelCodeEnum.isAlipay(request.getIssueChannelCode())
                && !CardTypeMapping.isAiShanDong(request.getCardType())) {
            appNotifyService.pushAlipayTripData(request);
        }
    }
}
