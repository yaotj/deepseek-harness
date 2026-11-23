package com.chinasofti.huateng.facepay.service.supplement;

import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterPayCommand;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterStatus;
import com.chinasofti.huateng.facepay.channel.paycenter.PayScene;
import com.chinasofti.huateng.facepay.entity.SupplementOrder;
import com.chinasofti.huateng.facepay.entity.SupplementOrderItem;
import com.chinasofti.huateng.facepay.mapper.SupplementOrderMapper;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayDebitConvergeRespDTO;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupplementPayCenterFlow {

    private static final Logger log = LoggerFactory.getLogger(SupplementPayCenterFlow.class);

    private static final String SUPPLEMENT_SUCCESS = "SUCCESS";
    private static final String SUPPLEMENT_FAIL = "FAIL";
    private static final String SUPPLEMENT_CLOSED = "CLOSED";

    private static final String ITEM_SETTLED = "SETTLED";
    private static final String ITEM_FAILED = "FAILED";

    private static final String DEBIT_SUCCESS = "SUCCESS";

    static final long NON_TERMINAL_CLOSE_HOURS = 24L;

    /**
     * 支付中心未按本次请求渠道受理（同一订单号下仍挂着旧渠道的待支付单）时的标记码。
     * 只用于「换渠道重下」这一条路径，调用方据此给乘客一句人话，NEVER 直接透给 APP。
     */
    public static final String CODE_CHANNEL_LOCKED = "CHANNEL_LOCKED";

    /**
     * 支付中心受理了，但本次渠道/参数没能写回本地（UPDATE 0 行，状态被并发改写或已非可支付态）时的标记码。
     * 与 {@link #CODE_CHANNEL_LOCKED} 分开命名：一个是「支付中心不认新渠道」，一个是「本地没接住」，
     * 排查方向完全不同，但给乘客的文案一致。
     */
    public static final String CODE_CHANNEL_NOT_APPLIED = "CHANNEL_NOT_APPLIED";

    static final int CLOSED_LOOKBACK_HOURS = 24;

    static final int PENDING_LOOKBACK_HOURS = 7 * 24;

    private final PayCenterClient payCenterClient;
    private final PayCenterMessageFactory messageFactory;
    private final SupplementOrderMapper supplementOrderMapper;
    private final GateTxnPayClient gateTxnPayClient;
    private final BlacklistClient blacklistClient;

    public SupplementPayCenterFlow(PayCenterClient payCenterClient,
                                   PayCenterMessageFactory messageFactory,
                                   SupplementOrderMapper supplementOrderMapper,
                                   GateTxnPayClient gateTxnPayClient,
                                   BlacklistClient blacklistClient) {
        this.payCenterClient = payCenterClient;
        this.messageFactory = messageFactory;
        this.supplementOrderMapper = supplementOrderMapper;
        this.gateTxnPayClient = gateTxnPayClient;
        this.blacklistClient = blacklistClient;
    }

    /**
     * 按乘客在收银台选定的渠道向支付中心预下单。
     *
     * <p>渠道来自 IF8A-11 的 {@code payChannelCode}，NEVER 取 {@code SUPPLEMENT_ORDER.PAYMENT_VENDOR}：
     * 后者是原过闸订单的支付方式，仅作留痕（IT-014），拿它发报会把乘客选的渠道顶掉。</p>
     */
    public RpcOutcome preOrder(SupplementOrder order, String paymentVendor) {
        PayCenterPayCommand command = new PayCenterPayCommand(
                order.getOrderNo(),
                PayScene.APP,
                paymentVendor,
                null,
                order.getTotalAmount(),
                "过闸欠费补款" + order.getOrderCount() + "笔",
                "过闸欠费补款",
                null
        );

        PayCenterResult result = payCenterClient.execute(
                payCenterClient.properties().getPayUrl(),
                messageFactory.buildPayRequest(
                        command, payCenterClient.properties().effectiveSupplementNoticeUrl()));

        if (result.isTransportFailed()) {
            log.error("补款预下单支付中心未应答, orderNo={}, reason={}",
                    order.getOrderNo(), result.getFailureReason());
            return new RpcOutcome.Unreachable(
                    new RuntimeException("支付中心未应答: " + result.getFailureReason()));
        }

        if (!result.isSuccessCode()) {
            log.warn("补款预下单被支付中心拒绝, orderNo={}, vendor={}, code={}, msg={}",
                    order.getOrderNo(), paymentVendor, result.getCode(), result.getMsg());
            return new RpcOutcome.BizRejected(result.getCode(), result.getMsg());
        }

        // 支付中心未回传 payChannelCode 时以本次请求渠道为准（实测该字段常为 null）。
        // 若它明确回了别的渠道，说明同一订单号下仍挂着旧渠道的待支付单、本次换渠道没生效：
        // MUST 拒绝且**不写库**——把旧参数/旧渠道回给乘客，等于支付页显示的渠道与实际可付渠道不一致。
        String payChannelCode = result.string("payChannelCode");
        if (payChannelCode == null || payChannelCode.isBlank()) {
            payChannelCode = paymentVendor;
        } else if (!paymentVendor.equals(payChannelCode)) {
            log.warn("补款预下单 支付中心未按本次渠道受理, orderNo={}, requested={}, returned={}",
                    order.getOrderNo(), paymentVendor, payChannelCode);
            return new RpcOutcome.BizRejected(CODE_CHANNEL_LOCKED,
                    "支付中心仍挂旧渠道: requested=" + paymentVendor + ", returned=" + payChannelCode);
        }
        String merchantOrderNo = result.string("orderNo");
        String paymentInfo = result.string("data");

        int updated = supplementOrderMapper.updatePrepayResult(
                order.getOrderNo(), payChannelCode, merchantOrderNo, paymentInfo);
        if (updated == 0) {
            // 更新不到 = 本次渠道与参数没落到本地，库里还是上一版的渠道/参数。
            // MUST 当失败返回：否则调用方会接着把旧渠道的支付参数回给乘客，支付页显示与实际可付不一致
            // （2026-11-20 实测缺陷：该 UPDATE 曾只认 PAY_STATUS='INIT'，PROCESSING 换渠道静默 0 行）。
            log.warn("补款预下单 本次渠道未落库, orderNo={}, payChannelCode={}, merchantOrderNo={}",
                    order.getOrderNo(), payChannelCode, merchantOrderNo);
            return new RpcOutcome.BizRejected(CODE_CHANNEL_NOT_APPLIED,
                    "本次渠道未落库, payChannelCode=" + payChannelCode);
        }
        log.info("补款预下单成功, orderNo={}, merchantOrderNo={}, payChannelCode={}",
                order.getOrderNo(), merchantOrderNo, payChannelCode);
        return new RpcOutcome.Ok();
    }

    public void handlePayNotice(String orderNo) {
        SupplementOrder order = supplementOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.error("补款回调但补款单不存在, orderNo={}", orderNo);
            return;
        }

        PayCenterResult result = payCenterClient.execute(
                payCenterClient.properties().getQueryUrl(),
                messageFactory.buildQueryRequest(orderNo));

        if (result.isTransportFailed() || !result.isSuccessCode()) {
            log.warn("补款回调回查支付中心未得到有效结果，不推进状态, orderNo={}, transportFailed={}, code={}",
                    orderNo, result.isTransportFailed(), result.getCode());
            return;
        }

        PayCenterStatus status = result.status();
        if (status == PayCenterStatus.SUCCESS) {
            settleSuccess(order, result);
        } else if (status != null && status.isFailed()) {
            markFailed(order, result);
        } else {
            log.info("补款回调支付中心状态非终态，不推进, orderNo={}, status={}", orderNo, status);
        }
    }

    public int convergePending(int limit) {
        List<SupplementOrder> orders = supplementOrderMapper.selectPendingOrders(
                limit, CLOSED_LOOKBACK_HOURS, PENDING_LOOKBACK_HOURS);
        int converged = 0;
        for (SupplementOrder order : orders) {
            try {
                if (convergeOne(order)) {
                    converged++;
                }
            } catch (Exception e) {
                log.error("补款收敛异常, orderNo={}", order.getOrderNo(), e);
            }
        }
        return converged;
    }

    private boolean convergeOne(SupplementOrder order) {
        PayCenterResult result = payCenterClient.execute(
                payCenterClient.properties().getQueryUrl(),
                messageFactory.buildQueryRequest(order.getOrderNo()));

        if (result.isTransportFailed() || !result.isSuccessCode()) {
            log.warn("补款定时收敛回查支付中心未得到有效结果，跳过, orderNo={}, transportFailed={}",
                    order.getOrderNo(), result.isTransportFailed());
            return false;
        }

        PayCenterStatus status = result.status();
        if (status == PayCenterStatus.SUCCESS) {
            settleSuccess(order, result);
            return true;
        }
        if (status != null && status.isFailed()) {
            markFailed(order, result);
            return true;
        }
        if (status == PayCenterStatus.UNPAID) {
            int updated = supplementOrderMapper.updatePayStatusFromPending(
                    order.getOrderNo(), SUPPLEMENT_CLOSED, "支付中心返回未支付");
            if (updated > 0) {
                log.info("补款收敛发现未支付已过期，关单, orderNo={}", order.getOrderNo());
                return true;
            }
        }
        if (status == null) {
            log.warn("补款收敛回查返回未识别状态，请检查 PayCenterStatus 枚举是否漏配, orderNo={}, rawStatus={}",
                    order.getOrderNo(), result.string("status"));
            return false;
        }
        if (status.isNonTerminal() && isStale(order)) {
            int updated = supplementOrderMapper.updatePayStatusFromPending(
                    order.getOrderNo(), SUPPLEMENT_CLOSED,
                    "支付中心超 " + NON_TERMINAL_CLOSE_HOURS + "h 仍为" + status.getCode() + "，超时关单");
            if (updated > 0) {
                log.info("补款收敛发现非终态超龄，关单, orderNo={}, status={}", order.getOrderNo(), status);
                return true;
            }
        }
        return false;
    }


    private boolean isStale(SupplementOrder order) {
        java.time.LocalDateTime lastActivity = order.getUpdateTime() != null
                ? order.getUpdateTime() : order.getCreateTime();
        return lastActivity != null
                && lastActivity.isBefore(java.time.LocalDateTime.now().minusHours(NON_TERMINAL_CLOSE_HOURS));
    }

    private void settleSuccess(SupplementOrder order, PayCenterResult result) {
        int updated = supplementOrderMapper.updatePayStatusFromPending(
                order.getOrderNo(), SUPPLEMENT_SUCCESS, "支付成功");
        if (updated == 0) {
            SupplementOrder latest = supplementOrderMapper.selectByOrderNo(order.getOrderNo());
            if (latest != null && SUPPLEMENT_SUCCESS.equals(latest.getPayStatus())) {
                log.info("补款已是 SUCCESS，幂等跳过收敛, orderNo={}", order.getOrderNo());
                return;
            }
            log.warn("补款推进 SUCCESS 返回 0 行，状态可能已被并发改写, orderNo={}", order.getOrderNo());
            return;
        }

        List<SupplementOrderItem> items = supplementOrderMapper.selectItemsByOrderNo(order.getOrderNo());
        for (SupplementOrderItem item : items) {
            settleOneItem(order, item);
        }
        log.info("补款支付成功收敛完成, orderNo={}, itemCount={}", order.getOrderNo(), items.size());
        releaseBlacklistIfSettled(order);
    }

    /**
     * 补款付清后尽早解黑：调 blacklist-server 的单卡实时解黑（自验欠费、无欠费才解）。
     *
     * <p><b>失败只记日志、NEVER 阻断补款收敛主流程</b>：补款单已 SUCCESS、明细已 SETTLED，
     * 解黑只是体验加速。RPC 失败/超时时该卡仍会被 sys_job 230（每天 10/16 点全表 autoRelease）兜底解黑，
     * 因此这里不需要重试、不需要落本地补偿表。
     */
    private void releaseBlacklistIfSettled(SupplementOrder order) {
        String cardId = order.getCardId();
        if (cardId == null || cardId.isBlank()) {
            return;
        }
        try {
            var response = blacklistClient.releaseCardIfSettled(cardId, null);
            if (response != null && response.getFailed() > 0) {
                log.error("补款付清后单卡实时解黑存在失败记录, orderNo={}, cardId={}, response={}",
                        order.getOrderNo(), cardId, response);
                return;
            }
            log.info("补款付清后单卡实时解黑完成, orderNo={}, cardId={}, released={}, unsettled={}",
                    order.getOrderNo(), cardId,
                    response == null ? null : response.getReleased(),
                    response == null ? null : response.getUnsettled());
        } catch (RuntimeException e) {
            // 不向外抛：解黑失败不能把已成功的补款收敛变成异常，兜底任务会再解。
            log.error("补款付清后单卡实时解黑调用失败（由每日解黑任务兜底）, orderNo={}, cardId={}",
                    order.getOrderNo(), cardId, e);
        }
    }

    private void settleOneItem(SupplementOrder order, SupplementOrderItem item) {
        GateTxnPayDebitConvergeReqDTO request = new GateTxnPayDebitConvergeReqDTO();
        request.setOrigOrderNo(item.getOrigOrderNo());
        request.setTxnDate(item.getOrigTxnDate());
        request.setRemark("补款单号:" + order.getOrderNo());

        GateTxnPayDebitConvergeRespDTO response = gateTxnPayClient.convergeDebitStatusForSupplement(request);
        if (response != null && Boolean.TRUE.equals(response.getConverged())) {
            supplementOrderMapper.updateItemSettleStatus(
                    order.getOrderNo(), item.getOrigOrderNo(), ITEM_SETTLED, null);
            return;
        }

        String debitStatus = response != null ? response.getDebitStatus() : null;
        if (DEBIT_SUCCESS.equals(debitStatus)) {
            log.warn("补款明细对应行程已被先到补款单结清，本单系重复支付，待退款, orderNo={}, origOrderNo={}",
                    order.getOrderNo(), item.getOrigOrderNo());
            supplementOrderMapper.updateItemSettleStatus(
                    order.getOrderNo(), item.getOrigOrderNo(), ITEM_FAILED, "重复支付待退款");
            return;
        }

        log.error("补款收敛原订单失败，需人工核对, orderNo={}, origOrderNo={}, retCode={}, retMsg={}, debitStatus={}",
                order.getOrderNo(), item.getOrigOrderNo(),
                response != null ? response.getRetCode() : "no response",
                response != null ? response.getRetMsg() : null,
                debitStatus != null ? debitStatus : "unknown");
        supplementOrderMapper.updateItemSettleStatus(
                order.getOrderNo(), item.getOrigOrderNo(), ITEM_FAILED, "原订单收敛失败");
    }

    private void markFailed(SupplementOrder order, PayCenterResult result) {
        int updated = supplementOrderMapper.updatePayStatusFromPending(
                order.getOrderNo(), SUPPLEMENT_FAIL,
                "支付中心返回失败: " + result.getMsg());
        if (updated > 0) {
            log.info("补款已标记失败, orderNo={}", order.getOrderNo());
        }
    }
}
