package com.chinasofti.huateng.model.app.dailyticket;

/**
 * IF8A-70 请求旅游票下单响应参数。
 */
public class TravelTicketOrderResult extends DailyTicketBaseResult {
    /**
     * 旅游票单号，即聚合主单号。
     */
    private String orderNo;

    /**
     * 内含的日票子单号，多个子单号用英文逗号分隔。
     */
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
