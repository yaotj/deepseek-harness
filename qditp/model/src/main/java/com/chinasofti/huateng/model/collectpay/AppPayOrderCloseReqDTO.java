package com.chinasofti.huateng.model.collectpay;


public class AppPayOrderCloseReqDTO {

    private String orderNo;

    private String msg;

    public String getOrderNo() { return orderNo; }

    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getMsg() { return msg; }

    public void setMsg(String msg) { this.msg = msg; }

    @Override
    public String toString() {
        return "AppPayOrderCloseReqDTO{orderNo='" + orderNo + "', msg='" + msg + "'}";
    }
}
