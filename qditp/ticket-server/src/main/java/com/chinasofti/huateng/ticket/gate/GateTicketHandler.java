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

/** IF1A-01 闸机检票编排 —— 本类只做编排，不做计算。 */
@Component
class GateTicketHandler {

    private static final Logger log = LoggerFactory.getLogger(GateTicketHandler.class);

    /** QRCODE_STATUS 的唯一访问口。 */
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

    /** 出站扣费编排。 */
    @Autowired
    private GateFarePaymentOrchestrator farePaymentOrchestrator;

    /**
     * 处理闸机检票通知。
     *
     * @param request 闸机检票通知请求
     * @param cardId 卡ID
     * @param response 响应对象
     */
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

        handleGateTransaction(request, currentStatus, response);
    }

    /** 检票主流程编排。 */
    private void handleGateTransaction(NotifyVerifyResultReqDTO request, QRCodeStatus currentStatus,
                                       NotifyVerifyResultRespDTO response) {
        String resolvedCardType = CardTypeMapping.toIssueCardType(request.getSignChannelCode());

        if (!dailyTicketCoordinator.checkEntryAllowed(request, response, resolvedCardType)) {
            return;
        }

        String gateCardType = request.getCardType();
        cardTypeEnricher.applyActualCardType(request, response);

        request.setLastTicketStatus(currentStatus.getCodeStatus());
        request.setLastHandleStationCode(currentStatus.getLastTxnStation());
        if (!StringUtils.hasText(request.getLastHandleDateTime())) {
            request.setLastHandleDateTime(currentStatus.getLastTxnTime());
        }

        QRCodeTxnDetail detail = txnAssembler.buildTxnDetail(request);
        QRCodeStatus nextStatus = txnAssembler.buildNextStatus(request, currentStatus);
        log.info("IF1A-01 写入交易明细, detail = {}", detail);
        log.info("IF1A-01 更新票卡状态明细, detail = {}", nextStatus);

        QRCodeStatus effectiveStatus = writeAndAdvance(request, detail, nextStatus, currentStatus);

        dailyTicketCoordinator.markUsedOnExit(request, resolvedCardType);

        pushDownstream(request, effectiveStatus, gateCardType);

        responseAssembler.fillSuccessResponse(request, response, effectiveStatus, resolvedCardType);

        farePaymentOrchestrator.settleIfNeeded(request, response);
    }

    /** 明细入库 + 状态推进，两条本地 SQL 在 {@link GateTicketWriter} 的独立事务内完成。 */
    private QRCodeStatus writeAndAdvance(NotifyVerifyResultReqDTO request, QRCodeTxnDetail detail,
                                         QRCodeStatus nextStatus, QRCodeStatus currentStatus) {
        GateTicketWriter.WriteResult writeResult = gateTicketWriter.saveTxnAndAdvanceStatus(
                detail, nextStatus, currentStatus.getTxnSeq());
        QRCodeStatus effectiveStatus = writeResult.status();
        if (writeResult.duplicate()) {
            log.info("IF1A-01 闸机交易明细重复上送, 状态不再推进, cardId={}, trxType={}, handleDateTime={}, ticketTransSeq={}, deviceId={}, 库内useCount={}, 库内txnSeq={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(),
                    request.getTicketTransSeq(), request.getDeviceId(),
                    effectiveStatus.getUseCount(), effectiveStatus.getTxnSeq());
        }
        log.info("IF1A-01 更新票卡状态完成, cardId={}, isDuplicate={}, nextStatusCode={}, updateCount={}",
                request.getCardId(), writeResult.duplicate(), effectiveStatus.getCodeStatus(),
                writeResult.updateCount());
        return effectiveStatus;
    }

    /** HCE 卡仅回写；非 HCE 卡推进行业数据；支付宝渠道额外推送行程数据。 */
    private void pushDownstream(NotifyVerifyResultReqDTO request, QRCodeStatus effectiveStatus,
                                String gateCardType) {
        String cardType = request.getCardType();
        if (isHceCard(cardType)) {
            log.info("IF1A-01 HCE卡数据回写, cardId={}, cardType={}", request.getCardId(), cardType);
            cardTypeEnricher.updateHceDataFromGateTransaction(request);
            return;
        }
        appNotifyService.notifyVerifyResult(request, effectiveStatus, gateCardType);

        if (IssueChannelCodeEnum.isAlipay(request.getIssueChannelCode())
                && !CardTypeMapping.isAiShanDong(request.getCardType())) {
            appNotifyService.pushAlipayTripData(request);
        }
    }
}
