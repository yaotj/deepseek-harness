package com.chinasofti.huateng.gatetxnpay.model.page;

/** 运营端发起过闸扣费退款的请求参数，金额单位为分。 */
public class GateTxnPayRefundRequest {
    private Integer refundAmount;
    private String refundReason;

    public Integer getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Integer refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }
}
