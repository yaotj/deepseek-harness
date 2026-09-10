package com.chinasofti.huateng.model.app.dailyticket;

import java.util.List;

/**
 * IF8A-70 请求旅游票下单响应参数。
 */
public class TravelTicketOrderResult extends DailyTicketBaseResult {
    /**
     * 旅游票单号，即聚合主单号。
     */
    private String orderNo;

    /**
     * 内含的日票子单列表。
     */
    private List<TravelTicketSubOrder> subOrders;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public List<TravelTicketSubOrder> getSubOrders() {
        return subOrders;
    }

    public void setSubOrders(List<TravelTicketSubOrder> subOrders) {
        this.subOrders = subOrders;
    }
}
