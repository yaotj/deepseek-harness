package com.chinasofti.huateng.dailyticket.model;

import java.util.Date;

/**
 * 旅游票聚合主单内部模型。
 *
 * <p>对应表 {@code TRAVEL_TICKET_ORDER}。主单只承载聚合信息与支付状态，
 * 内含的每张日票落在 {@code DAILY_TICKET_ORDER}，通过 {@code PARENT_ORDER_NO} 回指本主单。</p>
 */
public class TravelTicketOrder {
    /**
     * 主键ID。
     */
    private String id;

    /**
     * 旅游票单号。
     */
    private String orderNo;

    /**
     * APP用户编号。
     */
    private String userId;

    /**
     * APP侧卡类型。
     */
    private String cardType;

    /**
     * 展示类型。
     */
    private String showType;

    /**
     * 单张票价，单位分。
     */
    private Integer ticketPrice;

    /**
     * 购买数量，即内含日票张数。
     */
    private Integer ticketCount;

    /**
     * 总金额，单位分。由 ticketPrice * ticketCount 服务端重算得到。
     */
    private Integer totalAmount;

    /**
     * 订单来源。
     */
    private String orderSource;

    /**
     * 订单状态，取值与日票一致：CREATED/PAYING/PAID/PAY_FAILED/CANCELED/REFUNDING/REFUNDED。
     */
    private String orderStatus;

    /**
     * 支付状态，取值与日票一致：INIT/PAYING/PAID/FAIL。
     */
    private String payStatus;

    /**
     * 创建时间。
     */
    private Date createTime;

    /**
     * 更新时间。
     */
    private Date updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

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

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public String getPayStatus() {
        return payStatus;
    }

    public void setPayStatus(String payStatus) {
        this.payStatus = payStatus;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
