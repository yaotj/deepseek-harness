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

/**
 * IF1A-01 闸机检票编排 —— <b>本类只做编排，不做计算</b>。
 *
 * <p>2026-09-14 由 888 行的同名类拆成「一个编排 + 五个协作者」（ADR-D60）。原因不是行数，
 * 而是那 888 行里同时住着五件互不相关的事：状态码解析（纯函数）、落库对象组装（纯函数）、
 * 账户域富化（两个远端）、日票协同（三个远端、三套相反的失败处置）、应答装配。
 * 混住的代价是具体的：改任何一件都要读完全部，而 {@code SelfServiceSupplementCodeStatusTest}
 * 为了测一个纯函数得 {@code new} 出整个类。</p>
 *
 * <p>拆分后的编排步骤（顺序即不变量，<b>NEVER 重排</b>）：
 * <ol>
 *   <li>查当前票卡状态，查不到即 {@code 8004} 返回</li>
 *   <li>{@link GateDailyTicketCoordinator#checkEntryAllowed} 日票进站校验（仅进站方向）</li>
 *   <li>{@link GateCardTypeEnricher#applyActualCardType} 用账户域覆盖卡种 / 签约信息 —— <b>MUST 在组装之前</b></li>
 *   <li>{@link GateTxnAssembler} 组装明细与目标状态</li>
 *   <li>{@link GateTicketWriter} 独立事务内写两条本地 SQL</li>
 *   <li>{@link GateDailyTicketCoordinator#markUsedOnExit} 日票出站扣次（仅出站方向）</li>
 *   <li>HCE 回写 / 行业数据推送 / 支付宝行程推送</li>
 *   <li>{@link GateResponseAssembler#fillSuccessResponse} 填齐应答</li>
 * </ol>
 *
 * <p><b>本类与 {@link AgmRideStatusServiceImpl} 都 NEVER 加 {@code @Transactional}。</b>
 * 整条链路要调 6 个远端，其中 4 个发生在写库之后；事务包住整个方法会让
 * {@code QRCODE_STATUS} 按 {@code CARD_ID} 命中的单行锁持到全部 RPC 返回（每个 10s 超时，最坏 40s），
 * 同一张卡连续进出站与 AGM 超时重推会全部串行堆在这一行上；等待超过 Druid
 * {@code remove-abandoned-timeout=60} 后连接被强杀、{@code commit} 抛 {@code connection closed}，
 * 整个事务连同「留证据」的明细一起丢弃。详见 AGENTS.md §5.2 记录的 2026-08-26 生产事故
 * 与 {@link GateTicketWriter} 的类注释。事务边界已收窄到
 * {@link GateTicketWriter#saveTxnAndAdvanceStatus}。</p>
 */
@Component
class GateTicketHandler {

    private static final Logger log = LoggerFactory.getLogger(GateTicketHandler.class);

    /**
     * QRCODE_STATUS 的唯一访问口。**NEVER 改回直接注 {@code QRCodeStatusMapper}** ——
     * 该表的写权归 gate 包（见 {@link QRCodeStatusStore} 类注释）。
     */
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

    /**
     * 出站扣费编排。2026-09-14 从 fep-dev-server 迁入（见 {@link GateFarePaymentOrchestrator} 类注释）。
     */
    @Autowired
    private GateFarePaymentOrchestrator farePaymentOrchestrator;

    /**
     * 处理闸机检票通知。
     *
     * @param request  闸机检票通知请求
     * @param cardId   卡ID
     * @param response 响应对象
     */
    public void handleNotifyVerifyResult(NotifyVerifyResultReqDTO request, String cardId,
                                        NotifyVerifyResultRespDTO response) {
        // 入口日志前置，确保异常场景下请求参数不丢失
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
     * 检票主流程编排。步骤顺序见类注释，**NEVER 重排**。
     */
    private void handleGateTransaction(NotifyVerifyResultReqDTO request, QRCodeStatus currentStatus,
                                       NotifyVerifyResultRespDTO response) {
        // resolvedCardType 取自闸机上送的 signChannelCode，**与 request.cardType 语义不同**：
        // 后者稍后会被账户域覆盖，而日票判定必须认签约渠道位。NEVER 混用。
        String resolvedCardType = CardTypeMapping.toIssueCardType(request.getSignChannelCode());

        if (!dailyTicketCoordinator.checkEntryAllowed(request, response, resolvedCardType)) {
            return;
        }

        // 闸机上送的原始卡种 MUST 在 applyActualCardType 覆盖前留存：码体票种位要与 IF8A-03
        // （fep-app-server → industry-data-server）口径一致，即取上游上送值、不做 account 覆盖，
        // 否则同一张码在「APP 主动取码」与「过闸后反向推码」两条路径上会解析出不同票种。
        String gateCardType = request.getCardType();
        cardTypeEnricher.applyActualCardType(request, response);

        // 填充上次交易字段（无值时用当前状态兜底）
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

        // 出站扣费 MUST 是最后一步：入参里有 12 个字段取自上一行填齐的 response。
        // 本调用不抛异常、不改 response —— 落单失败只记 ERROR，闸机照常开门（乘客已在付费区）。
        farePaymentOrchestrator.settleIfNeeded(request, response);
    }

    /**
     * 明细入库 + 状态推进，两条本地 SQL 在 {@link GateTicketWriter} 的独立事务内完成。
     *
     * <p><b>返回值 MUST 用于后续对外输出与推送，NEVER 继续用 {@code nextStatus}</b>：
     * {@code nextStatus} 的 {@code useCount} / {@code txnSeq} 是「当前值 + 1」的相对增量。
     * AGM 超时重推时 {@link GateTicketWriter} 会跳过状态 upsert 并回传库里已持久化的那一行，
     * 此处若仍用 {@code nextStatus}，返回闸机的 {@code ticketStatus} 与推给行业数据的 {@code txnSeq}
     * 会比库里多 1（{@code txnSeq} 写进码体，{@code ticketTransSeq} 又是明细唯一索引的一部分，
     * 错位后幂等失效）。</p>
     */
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

    /**
     * HCE 卡仅回写；非 HCE 卡推进行业数据；支付宝渠道额外推送行程数据。
     *
     * <p>TODO 给支付宝出行推送时，补进站类型按正常 56 推，补出站需区分为 57。</p>
     */
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
            // 支付宝行业数据推送。2026-09-14 起该方法内部走 appNotifyExecutor 异步执行，
            // NEVER 改回同步：外部 HTTPS 往返最坏 20s，同步会直接加在过闸应答上、引来 AGM 重发。
            appNotifyService.pushAlipayTripData(request);
        }
    }
}
