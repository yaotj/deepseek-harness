package com.chinasofti.huateng.model.app.dailyticket;


public class TravelTicketOrderReqDTO {

    private String cardType;


    private String userId;


    private Integer ticketPrice;


    private Integer ticketCount;


    private Integer totalAmount;


    private String orderSource;


    private String showType;

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Integer getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Integer ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public Integer getTicketCount() {
        return ticketCount;
    }

    public void setTicketCount(Integer ticketCount) {
        this.ticketCount = ticketCount;
    }

    public Integer getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Integer totalAmount) {
        this.totalAmount = totalAmount;
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

    @Override
    public String toString() {
        return "TravelTicketOrderReqDTO{cardType='" + cardType + "', userId='" + userId
                + "', ticketPrice=" + ticketPrice + ", ticketCount=" + ticketCount
                + ", totalAmount=" + totalAmount + ", orderSource='" + orderSource
                + "', showType='" + showType + "'}";
    }
}
