package com.chinasofti.huateng.model.app;


public class RequestRefundReqDTO {

    private String orderNo;


    private Integer refundAmount;


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
