package com.chinasofti.huateng.model.app.dailyticket;

/**
 * IF8A-73 免费票请求下单响应参数。
 */
public class DailyTicketFreeOrderResult extends DailyTicketBaseResult {
    /**
     * 普通日票订单号，或旅游票聚合主单号。
     */
    private String orderNo;

    /**
     * 旅游票子单号 JSON 字符串数组，形如 ["0E..."]；普通日票为空。
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
