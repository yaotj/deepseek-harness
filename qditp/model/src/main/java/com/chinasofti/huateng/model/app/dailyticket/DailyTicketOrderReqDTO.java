package com.chinasofti.huateng.model.app.dailyticket;


public class DailyTicketOrderReqDTO {

    private String orderSource;


    private Integer ticketPrice;


    private String cardType;


    private String showType;


    private String userId;

    public String getOrderSource() {
        return orderSource;
    }

    public void setOrderSource(String orderSource) {
        this.orderSource = orderSource;
    }

    public Integer getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Integer ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getShowType() {
        return showType;
    }

    public void setShowType(String showType) {
        this.showType = showType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "DailyTicketOrderReqDTO{orderSource='" + orderSource + "', ticketPrice=" + ticketPrice
                + ", cardType='" + cardType + "', showType='" + showType + "', userId='" + userId + "'}";
    }
}
