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

    /**
     * 检票主流程编排。
     *
     * <p>AGM 超时重发时 {@code writeResult.duplicate()} 为 true，此时 <b>扣次与出向通知一律不重跑</b>：
     * 两者都不按业务键幂等 —— {@code markUsedOnExit} 每调一次就在 daily-ticket 侧真扣一次次数
     * （且扣成功后还会发 §3.63 通知），{@code pushDownstream} 会把同一笔行程二次推给 APP 与支付宝。
     * 首次上送已经把这两步做完了，重跑只会造成乘客少一次乘车次数、下游多一条行程。
     * 首次上送时这两步若失败，现状本就是「只留 ERROR 日志、转人工核对」
     * （见 {@link GateDailyTicketCoordinator} 的 {@code PASS_THROUGH} 口径），
     * NEVER 靠 AGM 重发去兜 —— 重发既不保证发生、也分不清首次到底成没成。
     *
     * <p>{@code fillSuccessResponse} 与 {@code settleIfNeeded} <b>刻意留在 duplicate 分支之外</b>：
     * 前者是闸机的应答，重复上送也 MUST 照常给（用库内 persisted 状态填，NEVER 让 AGM 拿不到应答）；
     * 后者的下游 {@code gate-txn-pay.requestPay} 按 {@code UK_GATE_TXN_PAY_BIZ} + {@code orderNo} 幂等、
     * 重跑不会重复扣款，而 {@link GateFarePaymentOrchestrator} 的 RPC 异常是就地吞掉的、本模块没有扣费补偿载体，
     * 因此 AGM 重发是「首次扣费静默失败」唯一的自愈路径，NEVER 把它一起短路掉。
     *
     * <p><b>{@code resolvedCardType} MUST 取自账户侧富化后的 {@code request.getCardType()}，
     * NEVER 退回 {@code CardTypeMapping.toIssueCardType(request.getSignChannelCode())}。</b>
     * 后者是 2026-09-22 修掉的 P0：{@code signChannelCode} 是<b>签约渠道代码</b>（十六进制，
     * 见 {@link com.chinasofti.huateng.model.utils.SignChannelUtils}，`17` 还被 {@link GateResponseAssembler}
     * 当离线码标识用），而 {@code toIssueCardType} 的入参契约是 <b>APP 票种码</b>；两者仅在 {@code 12~15}
     * 上偶然重合（映射表里 {@code 12→0445}…{@code 15→0448}），因此那段代码看着能跑、实际只在这四个值上正确。
     * 闸机送 {@code 0x03} 时兜底原样返回、送 {@code 03} 时被映射成 {@code 0442}（HCE 卡），
     * 两种都让 {@code isDailyTicket} 返 false ⇒ 进站校验、出站扣次、应答的 {@code countingFlag} /
     * {@code countingTimes} / {@code ticketCode} 三处同时失效：实测一日票卡（{@code CARD_TYPE=0445}、
     * 实例 {@code ACTIVATED}）正常出站时 {@code QRCODE_STATUS} 照常推进，但 {@code DAILY_TICKET_INSTANCE}
     * 零变化、{@code USAGE_LOG} 0 行、对 daily-ticket 零次 RPC，等于日票/计次票在闸机链路里从未生效。
     *
     * <p>连带约束：{@code applyActualCardType} MUST 在 {@code checkEntryAllowed} <b>之前</b>调用 ——
     * 只有它能拿到真实卡种（account 域，查不到时回落支付宝出行账户域），进站校验没有它就无从判断是不是日票。
     * {@code gateCardType} 仍 MUST 在富化前抓取，它是闸机上送的原值、{@code pushDownstream} 要按原值推行业数据。
     */
    private void handleGateTransaction(NotifyVerifyResultReqDTO request, QRCodeStatus currentStatus,
                                       NotifyVerifyResultRespDTO response) {
        String gateCardType = request.getCardType();
        cardTypeEnricher.applyActualCardType(request, response);
        String resolvedCardType = CardTypeMapping.toIssueCardType(request.getCardType());
        log.info("IF1A-01 卡种判定完成, cardId={}, 闸机上送cardType={}, 富化后cardType={}, resolvedCardType={}, signChannelCode={}",
                request.getCardId(), gateCardType, request.getCardType(), resolvedCardType,
                request.getSignChannelCode());

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

            pushDownstream(request, effectiveStatus, gateCardType);
        }

        responseAssembler.fillSuccessResponse(request, response, effectiveStatus, resolvedCardType);

        farePaymentOrchestrator.settleIfNeeded(request, response);
    }

    /** 明细入库 + 状态推进，两条本地 SQL 在 {@link GateTicketWriter} 的独立事务内完成。 */
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
