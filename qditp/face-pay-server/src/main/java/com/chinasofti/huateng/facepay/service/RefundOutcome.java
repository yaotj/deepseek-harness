package com.chinasofti.huateng.facepay.service;

/**
 * 一次退款请求的结果。
 *
 * <p><b>调用方 MUST 检查 {@link #alreadyExisted()}</b>：为 true 表示这笔「原订单 + 票 + 来源」
 * 之前已经退过，本次没有新发起任何退款，调用方 NEVER 再触发下游动作（改票状态、发通知）。
 * 旧实现的 {@code doRefund} 返回 boolean 且恒为 true，调用方无从区分，
 * 结果是重复退款与「失败也标记成已退」同时存在。</p>
 *
 * @param refundNo       退款单号；{@link #isRejected()} 时为 null
 * @param refundStatus   退款单当前状态，取值同 {@code CK_F2F_REFUND_STATUS}
 * @param alreadyExisted 是否命中幂等（本次未新发起）
 * @param failureReason  被拒或未受理的原因，成功受理时为 null
 */
public record RefundOutcome(String refundNo,
                            String refundStatus,
                            boolean alreadyExisted,
                            String failureReason) {

    /** 参数非法等前置拒绝，连退款单都没落库。 */
    public static RefundOutcome rejected(String reason) {
        return new RefundOutcome(null, null, false, reason);
    }

    /** 是否被前置拒绝（没有退款单号）。 */
    public boolean isRejected() {
        return refundNo == null;
    }

    /**
     * 是否已被支付中心受理（{@code PROCESSING}）或已成功。
     *
     * <p><b>受理不等于退款成功</b>：钱到账要等 {@code F2fRefundService.reconcileRefund}
     * 查询确认。对设备回「已受理」是既有契约（旧实现连受理与否都不看，一律回成功）。</p>
     */
    public boolean isAccepted() {
        return F2fRefundService.STATUS_PROCESSING.equals(refundStatus)
                || F2fRefundService.STATUS_SUCCESS.equals(refundStatus);
    }
}
