package com.chinasofti.huateng.model.app.dailyticket;

/**
 * IF8A-70 旅游票子单信息。
 *
 * <p>一条子单对应一张日票（{@code DAILY_TICKET_ORDER} 一行）。下单阶段尚未支付，
 * 因此不含票编码与虚拟卡号，这些字段在支付成功激活后才产生。</p>
 */
public class TravelTicketSubOrder {
    /**
     * 子单号，即日票订单号。
     */
    private String orderNo;

    /**
     * APP侧卡类型，与主单一致。
     */
    private String cardType;

    /**
     * 展示类型，与主单一致。
     */
    private String showType;

    /**
     * 单张票价，单位分。
     */
    private Integer ticketPrice;

    /**
     * 子单状态，下单后为CREATED。
     */
    private String orderStatus;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
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

    public Integer getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Integer ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }
}
