package com.chinasofti.huateng.model.collectpay;


public class AppPayOrderQueryReqDTO {

    private String orderNo;

    public String getOrderNo() { return orderNo; }

    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    @Override
    public String toString() {
        return "AppPayOrderQueryReqDTO{orderNo='" + orderNo + "'}";
    }
}
