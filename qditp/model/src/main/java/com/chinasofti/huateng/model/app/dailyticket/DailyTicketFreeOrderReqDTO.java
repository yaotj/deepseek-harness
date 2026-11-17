package com.chinasofti.huateng.model.app.dailyticket;


public class DailyTicketFreeOrderReqDTO {

    private String userId;


    private String cardType;


    private Integer ticketPrice;


    private String orderSource;


    private String showType;


    private String orderType;


    private String payChannelCode;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public Integer getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Integer ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getOrderSource() {
        return orderSource;
    }

    public void setOrderSource(String orderSource) {
        this.orderSource = orderSource;
    }

    public String getShowType() {
        return showType;
    }

    public void setShowType(String showType) {
        this.showType = showType;
    }

    public String getOrderType() {
        return orderType;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }

    @Override
    public String toString() {
        return "DailyTicketFreeOrderReqDTO{userId='" + userId + "', cardType='" + cardType
                + "', ticketPrice=" + ticketPrice + ", orderSource='" + orderSource
                + "', showType='" + showType + "', orderType='" + orderType
                + "', payChannelCode='" + payChannelCode + "'}";
    }
}
