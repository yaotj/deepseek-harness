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

    /** 原过闸订单的扣费终态取值，与 gate-txn-pay 侧的 `DEBIT_STATUS` 口径一致。 */
    private static final String DEBIT_SUCCESS = "SUCCESS";

    private final PayCenterClient payCenterClient;
    private final PayCenterMessageFactory messageFactory;
    private final SupplementOrderMapper supplementOrderMapper;
    /**
     * 收敛原过闸订单只能走这个 RPC。
     *
     * <p>2026-09-16 前本类直接用 face-pay 自己的 `GateTxnPayMapper.convergeDebitStatus` UPDATE
     * `GATE_TXN_PAY`，那张表的 owner 是 gate-txn-pay-server —— 跨域直写热路径表，
     * 且同一条语义在两个模块各有一份，白名单一旦漂移就是资金账不平。
     * 那份 mapper 语句已删，<b>NEVER 加回、NEVER 在本类里再注入 face-pay 的 GateTxnPayMapper 做写操作</b>。</p>
     */
    private final GateTxnPayClient gateTxnPayClient;

    public SupplementPayCenterFlow(PayCenterClient payCenterClient,
                                   PayCenterMessageFactory messageFactory,
                                   SupplementOrderMapper supplementOrderMapper,
                                   GateTxnPayClient gateTxnPayClient) {
        this.payCenterClient = payCenterClient;
        this.messageFactory = messageFactory;
        this.supplementOrderMapper = supplementOrderMapper;
        this.gateTxnPayClient = gateTxnPayClient;
    }

    public RpcOutcome preOrder(SupplementOrder order) {
        PayCenterPayCommand command = new PayCenterPayCommand(
                order.getOrderNo(),
                PayScene.APP,
                order.getPaymentVendor(),
                null,
                order.getTotalAmount(),
                "过闸欠费补款" + order.getOrderCount() + "笔",
                "过闸欠费补款",
                null
        );

        // 回调地址 MUST 用补款专用的：通用 payNoticeUrl 指向 /itptvm/ci/tvm/payNotice，那里只查 F2F_ORDER，
        // 补款回调恒返 2001 订单不存在、被支付中心反复重推，状态只能靠 converge 兜（延迟 5 分钟）。见 ADR-D103。
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
            log.warn("补款预下单被支付中心拒绝, orderNo={}, code={}, msg={}",
                    order.getOrderNo(), result.getCode(), result.getMsg());
            return new RpcOutcome.BizRejected(result.getCode(), result.getMsg());
        }

        String payChannelCode = result.string("payChannelCode");
        String merchantOrderNo = result.string("orderNo");
        String paymentInfo = result.string("data");

        int updated = supplementOrderMapper.updatePrepayResult(
                order.getOrderNo(), payChannelCode, merchantOrderNo, paymentInfo);
        if (updated == 0) {
            log.info("补款预下单 updatePrepayResult 返回 0 行（可能已 PROCESSING 或被并发推进）, orderNo={}",
                    order.getOrderNo());
        } else {
            log.info("补款预下单成功, orderNo={}, merchantOrderNo={}",
                    order.getOrderNo(), merchantOrderNo);
        }
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
        List<SupplementOrder> orders = supplementOrderMapper.selectPendingOrders(limit, 24);
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
        return false;
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
    }

    /**
     * 收敛一条补款明细对应的原过闸订单。
     *
     * <p>一次 RPC 拿全三支判据（收口前是「本地 UPDATE + 0 行再 SELECT 回查」两次跨域访问）：</p>
     * <ul>
     *   <li>{@code converged=true} —— 本次把原订单推进到 SUCCESS，明细标 SETTLED</li>
     *   <li>{@code converged=false} 且原订单已 {@code SUCCESS} —— 已被先到的补款单结清，
     *       钱已实收而行程早已平账，本单是**重复支付待退款**，明细标 FAILED 留人工/对账闭环。
     *       <b>NEVER 当幂等成功标 SETTLED</b>，那等于把一笔该退的钱藏起来。</li>
     *   <li>其余（含对端返非 0000）—— 业务拒绝，重试无用，明细标 FAILED 留人工核对</li>
     * </ul>
     *
     * <p><b>异常（网络不可达）一律往外抛、不改本地状态</b>：本方法的调用链最终收在
     * {@code convergePending} 的 catch 或回调入口，明细留在未结清态，由
     * {@code SupplementOrderCloseProcessor.converge}（cron `0 *&#47;5 * * * ?`）下一轮重入。
     * <b>NEVER 在这里 catch 后把明细标成 FAILED</b> —— 那会把「可重试」写成终态。</p>
     */
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
            // 无独占后同一行程单可能被多张补款单各付成功一次：钱已实收但行程早已结清，
            // 本单属重复扣款，明细标 FAILED 并记「重复支付待退款」，由对账/人工退款闭环。
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
