package com.chinasofti.huateng.facepay.service;

/**
 * 一次退款请求的结果。
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

    /** 是否已被支付中心受理（{@code PROCESSING}）或已成功。 */
    public boolean isAccepted() {
        return F2fRefundService.STATUS_PROCESSING.equals(refundStatus)
                || F2fRefundService.STATUS_SUCCESS.equals(refundStatus);
    }
}
