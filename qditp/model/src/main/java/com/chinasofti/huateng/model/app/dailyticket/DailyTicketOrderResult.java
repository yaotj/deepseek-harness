package com.chinasofti.huateng.model.app.dailyticket;

/**
 * IF8A-60 日票下单响应参数。
 */
public class DailyTicketOrderResult extends DailyTicketBaseResult {
    /**
     * ITP日票订单号。
     */
    private String orderNo;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
}
