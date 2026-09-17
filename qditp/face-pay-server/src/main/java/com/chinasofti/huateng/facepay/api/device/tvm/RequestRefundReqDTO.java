package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/** TVM 主动退款请求报文。 */
public class RequestRefundReqDTO extends BaseDeviceRequest {

    /** 原订单号。 */
    private String orderNo;

    /** 退款原因，可空，原样落 {@code F2F_REFUND.REFUND_REASON}。 */
    private String refundReason;

    /** 退款金额，单位分。 */
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
