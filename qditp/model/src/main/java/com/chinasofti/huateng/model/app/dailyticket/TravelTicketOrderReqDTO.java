package com.chinasofti.huateng.model.app.dailyticket;

/**
 * IF8A-70 请求旅游票下单请求参数。
 *
 * <p>旅游票是聚合单，内含多张日票：主单记录总金额与购买数量，
 * 每张日票落一条子单（{@code DAILY_TICKET_ORDER}，{@code PARENT_ORDER_NO} 指向主单）。</p>
 */
public class TravelTicketOrderReqDTO {
    /**
     * 票卡类型，APP侧取值。
     */
    private String cardType;

    /**
     * APP用户编号。
     */
    private String userId;

    /**
     * 单张票价，单位分。
     */
    private Integer ticketPrice;

    /**
     * 购买数量，即内含日票张数。
     */
    private Integer ticketCount;

    /**
     * 总金额，单位分。服务端以 ticketPrice * ticketCount 重算后校验，不采信本字段落库。
     */
    private Integer totalAmount;

    /**
     * 订单来源。
     */
    private String orderSource;

    /**
     * 展示类型。
     */
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
