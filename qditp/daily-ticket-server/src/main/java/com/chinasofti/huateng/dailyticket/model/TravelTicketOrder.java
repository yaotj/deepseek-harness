package com.chinasofti.huateng.dailyticket.model;

import java.util.Date;

/** 旅游票聚合主单内部模型。 */
public class TravelTicketOrder {
    /** 主键ID。 */
    private String id;

    /** 旅游票单号。 */
    private String orderNo;

    /** APP用户编号。 */
    private String userId;

    /** APP侧卡类型。 */
    private String cardType;

    /** 展示类型。 */
    private String showType;

    /** 单张票价，单位分。 */
    private Integer ticketPrice;

    /** 购买数量，即内含日票张数。 */
    private Integer ticketCount;

    /** 总金额，单位分。由 ticketPrice * ticketCount 服务端重算得到。 */
    private Integer totalAmount;

    /** 订单来源。 */
    private String orderSource;

    /** 第三方用户编号。 */
    private String thirdUserId;

    /** 实付金额，单位分。 */
    private Integer payAmount;

    /** 支付渠道编码。 */
    private String payChannelCode;

    /** 支付渠道。 */
    private String channelType;

    /** 支付服务场景。 */
    private String payScene;

    /** 订单状态，取值与日票一致：CREATED/PAYING/PAID/PAY_FAILED/CANCELED/REFUNDING/REFUNDED。 */
    private String orderStatus;

    /** 支付状态，取值与日票一致：INIT/PAYING/PAID/FAIL。 */
    private String payStatus;

    /** 第三方支付交易号。 */
    private String tradeNo;

    /** 支付系统订单号。 */
    private String paymentOrderNo;

    /** 支付完成时间。 */
    private Date payDate;

    /** 创建时间。 */
    private Date createTime;

    /** 更新时间。 */
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

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public Integer getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(Integer payAmount) {
        this.payAmount = payAmount;
    }

    public String getPayChannelCode() {
        return payChannelCode;
    }

    public void setPayChannelCode(String payChannelCode) {
        this.payChannelCode = payChannelCode;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getPayScene() {
        return payScene;
    }

    public void setPayScene(String payScene) {
        this.payScene = payScene;
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

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    public String getPaymentOrderNo() {
        return paymentOrderNo;
    }

    public void setPaymentOrderNo(String paymentOrderNo) {
        this.paymentOrderNo = paymentOrderNo;
    }

    public Date getPayDate() {
        return payDate;
    }

    public void setPayDate(Date payDate) {
        this.payDate = payDate;
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
