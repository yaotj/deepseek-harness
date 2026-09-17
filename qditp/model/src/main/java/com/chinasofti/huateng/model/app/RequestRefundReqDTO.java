package com.chinasofti.huateng.model.app;

/**
 * 支付 API 3.1 请求退款内部入参。
 */
public class RequestRefundReqDTO {
    /**
     * 原支付订单号。
     */
    private String orderNo;

    /**
     * 退款金额，单位分。
     */
    private Integer refundAmount;

    /**
     * 退款理由。
     */
    private String refundReason;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

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

    @Override
    public String toString() {
        return "RequestRefundReqDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", refundAmount=" + refundAmount +
                ", refundReason='" + refundReason + '\'' +
                '}';
    }
}
