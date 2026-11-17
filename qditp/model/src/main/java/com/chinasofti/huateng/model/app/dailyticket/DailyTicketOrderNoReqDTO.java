package com.chinasofti.huateng.model.app.dailyticket;


public class DailyTicketOrderNoReqDTO {

    private String orderType;


    private String orderNo;

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    @Override
    public String toString() {
        return "DailyTicketOrderNoReqDTO{orderType='" + orderType + "', orderNo='" + orderNo + "'}";
    }
}
