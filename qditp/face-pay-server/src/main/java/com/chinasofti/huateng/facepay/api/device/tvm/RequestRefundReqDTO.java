package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * TVM 主动退款请求报文。
 * 对应 {@code POST /itptvm/ci/tvm/requestRefund} 的 {@code bizData}。
 *
 * <p><b>又是一套独立错误码</b>：旧实现校验失败返回 {@code retCode=9999}
 * 「订单号和退款金额不能为空」，与 2xxx / 8999 都不同。既有契约，照搬。</p>
 *
 * <p>旧实现的 {@code Integer.valueOf(refundAmt)} 无保护且不校验上限，
 * 可以退出比原订单更多的钱。本实现用 {@link #amountInFen()} 返回 null，
 * 并在 service 里对「超过原订单金额」直接拒绝。</p>
 */
public class RequestRefundReqDTO extends BaseDeviceRequest {

    /** 原订单号。必填。 */
    private String orderNo;

    /** 退款原因，可空，原样落 {@code F2F_REFUND.REFUND_REASON}。 */
    private String refundReason;

    /** 退款金额，单位分。必填。 */
    private String refundAmt;

    /**
     * 退款金额转 {@code Long}（分）。
     *
     * @return 金额；为空、非数字或非正数时返回 null，调用方 MUST 据此拒绝
     */
    public Long amountInFen() {
        if (refundAmt == null || refundAmt.isBlank()) {
            return null;
        }
        try {
            long amount = Long.parseLong(refundAmt.trim());
            return amount > 0 ? amount : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public String getRefundAmt() {
        return refundAmt;
    }

    public void setRefundAmt(String refundAmt) {
        this.refundAmt = refundAmt;
    }

    @Override
    public String toString() {
        return super.toString() + ",RequestRefundReqDTO{orderNo=" + orderNo
                + ", refundAmt=" + refundAmt
                + ", refundReason=" + refundReason + '}';
    }
}
