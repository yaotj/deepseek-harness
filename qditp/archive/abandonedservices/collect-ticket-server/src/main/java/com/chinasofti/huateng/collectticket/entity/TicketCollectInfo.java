package com.chinasofti.huateng.collectticket.entity;

import java.time.LocalDateTime;


public class TicketCollectInfo {

    private String orderNo;


    private String userId;


    private String entryStationCode;


    private String exitStationCode;


    private Integer ticketPrice;


    private Integer singelTicketNum;


    private Integer singleTicketType;


    private String channelCode;


    private Integer orderStatus;


    private LocalDateTime createTms;


    private String deviceId;


    private LocalDateTime qrcodeGenDate;


    private String randomFact;


    private LocalDateTime collectTms;


    private Integer collectStatus;


    private String tradeNo;


    private String payChannelCode;


    private String channelType;


    private Integer payResult;


    private Integer payAmount;


    private Integer cashAmount;


    private Integer couponAmount;


    private LocalDateTime payDate;


    private LocalDateTime refundTms;


    private Integer refundAmt;


    private Integer refundResult;


    private String refundReason;

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

    public String getEntryStationCode() {
        return entryStationCode;
    }

    public void setEntryStationCode(String entryStationCode) {
        this.entryStationCode = entryStationCode;
    }

    public String getExitStationCode() {
        return exitStationCode;
    }

    public void setExitStationCode(String exitStationCode) {
        this.exitStationCode = exitStationCode;
    }

    public Integer getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Integer ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public Integer getSingelTicketNum() {
        return singelTicketNum;
    }

    public void setSingelTicketNum(Integer singelTicketNum) {
        this.singelTicketNum = singelTicketNum;
    }

    public Integer getSingleTicketType() {
        return singleTicketType;
    }

    public void setSingleTicketType(Integer singleTicketType) {
        this.singleTicketType = singleTicketType;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public Integer getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(Integer orderStatus) {
        this.orderStatus = orderStatus;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public LocalDateTime getQrcodeGenDate() {
        return qrcodeGenDate;
    }

    public void setQrcodeGenDate(LocalDateTime qrcodeGenDate) {
        this.qrcodeGenDate = qrcodeGenDate;
    }

    public String getRandomFact() {
        return randomFact;
    }

    public void setRandomFact(String randomFact) {
        this.randomFact = randomFact;
    }

    public LocalDateTime getCollectTms() {
        return collectTms;
    }

    public void setCollectTms(LocalDateTime collectTms) {
        this.collectTms = collectTms;
    }

    public Integer getCollectStatus() {
        return collectStatus;
    }

    public void setCollectStatus(Integer collectStatus) {
        this.collectStatus = collectStatus;
    }

    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
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

    public Integer getPayResult() {
        return payResult;
    }

    public void setPayResult(Integer payResult) {
        this.payResult = payResult;
    }

    public Integer getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(Integer payAmount) {
        this.payAmount = payAmount;
    }

    public Integer getCashAmount() {
        return cashAmount;
    }

    public void setCashAmount(Integer cashAmount) {
        this.cashAmount = cashAmount;
    }

    public Integer getCouponAmount() {
        return couponAmount;
    }

    public void setCouponAmount(Integer couponAmount) {
        this.couponAmount = couponAmount;
    }

    public LocalDateTime getPayDate() {
        return payDate;
    }

    public void setPayDate(LocalDateTime payDate) {
        this.payDate = payDate;
    }

    public LocalDateTime getRefundTms() {
        return refundTms;
    }

    public void setRefundTms(LocalDateTime refundTms) {
        this.refundTms = refundTms;
    }

    public Integer getRefundAmt() {
        return refundAmt;
    }

    public void setRefundAmt(Integer refundAmt) {
        this.refundAmt = refundAmt;
    }

    public Integer getRefundResult() {
        return refundResult;
    }

    public void setRefundResult(Integer refundResult) {
        this.refundResult = refundResult;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    @Override
    public String toString() {
        return "TicketCollectInfo{" +
                "orderNo='" + orderNo + '\'' +
                ", userId='" + userId + '\'' +
                ", entryStationCode='" + entryStationCode + '\'' +
                ", exitStationCode='" + exitStationCode + '\'' +
                ", ticketPrice=" + ticketPrice +
                ", singelTicketNum=" + singelTicketNum +
                ", singleTicketType=" + singleTicketType +
                ", channelCode='" + channelCode + '\'' +
                ", orderStatus=" + orderStatus +
                ", createTms=" + createTms +
                ", deviceId='" + deviceId + '\'' +
                ", collectStatus=" + collectStatus +
                ", tradeNo='" + tradeNo + '\'' +
                ", payResult=" + payResult +
                '}';
    }
}
