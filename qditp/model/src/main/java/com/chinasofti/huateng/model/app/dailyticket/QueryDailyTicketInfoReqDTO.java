package com.chinasofti.huateng.model.app.dailyticket;

/**
 * 查询日票信息请求（按订单号或卡号查询ticketCode和actualTimes）。
 */
public class QueryDailyTicketInfoReqDTO {
    private String orderNo;
    private String cardId;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    @Override
    public String toString() {
        return "QueryDailyTicketInfoReqDTO{orderNo='" + orderNo + "', cardId='" + cardId + "'}";
    }
}
