package com.chinasofti.huateng.dailyticket.page;

import java.util.Date;

/** 退款操作页展示的日票订单摘要。金额单位为分。 */
public class DailyTicketRefundOrderView {
    private String orderNo;
    private String orderType;
    private String parentOrderNo;
    private Integer ticketCount;
    private Integer totalAmount;
    private String paymentOrderNo;
    private String tradeNo;
    private String ticketName;
    private Integer ticketPrice;
    private Integer payAmount;
    private String payChannelCode;
    private String orderStatus;
    private String payStatus;
    private Date payDate;
    private Date createTime;
    private String ticketStatus;
    private String cardNum;
    private String refundOrderNo;
    private String platformRefundNo;
    private String refundStatus;
    private String refundType;
    private Integer refundAmount;
    private Date refundDate;
    private Date verifyAfterTime;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public String getParentOrderNo() { return parentOrderNo; }
    public void setParentOrderNo(String parentOrderNo) { this.parentOrderNo = parentOrderNo; }
    public Integer getTicketCount() { return ticketCount; }
    public void setTicketCount(Integer ticketCount) { this.ticketCount = ticketCount; }
    public Integer getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Integer totalAmount) { this.totalAmount = totalAmount; }
    public String getPaymentOrderNo() { return paymentOrderNo; }
    public void setPaymentOrderNo(String paymentOrderNo) { this.paymentOrderNo = paymentOrderNo; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public String getTicketName() { return ticketName; }
    public void setTicketName(String ticketName) { this.ticketName = ticketName; }
    public Integer getTicketPrice() { return ticketPrice; }
    public void setTicketPrice(Integer ticketPrice) { this.ticketPrice = ticketPrice; }
    public Integer getPayAmount() { return payAmount; }
    public void setPayAmount(Integer payAmount) { this.payAmount = payAmount; }
    public String getPayChannelCode() { return payChannelCode; }
    public void setPayChannelCode(String payChannelCode) { this.payChannelCode = payChannelCode; }
    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }
    public String getPayStatus() { return payStatus; }
    public void setPayStatus(String payStatus) { this.payStatus = payStatus; }
    public Date getPayDate() { return payDate; }
    public void setPayDate(Date payDate) { this.payDate = payDate; }
    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }
    public String getTicketStatus() { return ticketStatus; }
    public void setTicketStatus(String ticketStatus) { this.ticketStatus = ticketStatus; }
    public String getCardNum() { return cardNum; }
    public void setCardNum(String cardNum) { this.cardNum = cardNum; }
    public String getRefundOrderNo() { return refundOrderNo; }
    public void setRefundOrderNo(String refundOrderNo) { this.refundOrderNo = refundOrderNo; }
    public String getPlatformRefundNo() { return platformRefundNo; }
    public void setPlatformRefundNo(String platformRefundNo) { this.platformRefundNo = platformRefundNo; }
    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }
    public String getRefundType() { return refundType; }
    public void setRefundType(String refundType) { this.refundType = refundType; }
    public Integer getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Integer refundAmount) { this.refundAmount = refundAmount; }
    public Date getRefundDate() { return refundDate; }
    public void setRefundDate(Date refundDate) { this.refundDate = refundDate; }
    public Date getVerifyAfterTime() { return verifyAfterTime; }
    public void setVerifyAfterTime(Date verifyAfterTime) { this.verifyAfterTime = verifyAfterTime; }
}
