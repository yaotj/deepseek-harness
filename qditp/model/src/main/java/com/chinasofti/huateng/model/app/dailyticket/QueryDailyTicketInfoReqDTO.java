package com.chinasofti.huateng.model.app.dailyticket;

/**
 * 查询日票信息请求（按订单号查询ticketCode和actualTimes）。
 */
public class QueryDailyTicketInfoReqDTO {
    private String orderNo;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }
}
