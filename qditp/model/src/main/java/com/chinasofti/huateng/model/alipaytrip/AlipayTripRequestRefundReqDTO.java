package com.chinasofti.huateng.model.alipaytrip;


public class AlipayTripRequestRefundReqDTO {


    private String orderNo;


    private String refundAmount;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(String refundAmount) {
        this.refundAmount = refundAmount;
    }
}
