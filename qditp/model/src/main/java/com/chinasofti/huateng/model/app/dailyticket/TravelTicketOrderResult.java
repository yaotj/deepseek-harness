package com.chinasofti.huateng.model.app.dailyticket;


public class TravelTicketOrderResult extends DailyTicketBaseResult {

    private String orderNo;


    private String subOrders;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getSubOrders() {
        return subOrders;
    }

    public void setSubOrders(String subOrders) {
        this.subOrders = subOrders;
    }
}
