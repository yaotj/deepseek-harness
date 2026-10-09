package com.chinasofti.huateng.model.app.dailyticket;

/**
 * IF8A-73 免费票请求下单请求参数。
 */
public class DailyTicketFreeOrderReqDTO {
    /**
     * APP用户编号。
     */
    private String userId;

    /**
     * 票卡类型，APP侧取值。
     */
    private String cardType;

    /**
     * 票面金额，单位分。免费下单实付金额固定为0，本字段仍用于票面/业务金额落库。
     */
    private Integer ticketPrice;

    /**
     * 订单来源。
     */
    private String orderSource;

    /**
     * 展示票类型。
     */
    private String showType;

    /**
     * 订单类型：1-普通日票，2-旅游票/泉城通日票。
     */
    private String orderType;

    /**
     * 免费下单支付渠道编码，例如手动发放、兑换等。
     */
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
