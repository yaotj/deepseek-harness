package com.chinasofti.huateng.model.app.dailyticket;

/**
 * 日票按订单号操作的通用请求参数。
 */
public class DailyTicketOrderNoReqDTO {
    /**
     * 订单类型，日票固定为1。
     */
    private String orderType;

    /**
     * 日票订单号。
     */
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
}
