package com.chinasofti.huateng.collectticket.entity;

import java.time.LocalDateTime;


public class TicketCollectLogs {

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


    private LocalDateTime collectTms;


    private Integer collectStatus;


    private String deviceId;


    private LocalDateTime qrcodeGenDate;


    private String randomFact;


    private Integer actualTakeTicketNum;


    private String faultSlipSeq;


    private String errorCode;


    private String errorMessage;

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

    public Integer getActualTakeTicketNum() {
        return actualTakeTicketNum;
    }

    public void setActualTakeTicketNum(Integer actualTakeTicketNum) {
        this.actualTakeTicketNum = actualTakeTicketNum;
    }

    public String getFaultSlipSeq() {
        return faultSlipSeq;
    }

    public void setFaultSlipSeq(String faultSlipSeq) {
        this.faultSlipSeq = faultSlipSeq;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @Override
    public String toString() {
        return "TicketCollectLogs{" +
                "orderNo='" + orderNo + '\'' +
                ", userId='" + userId + '\'' +
                ", entryStationCode='" + entryStationCode + '\'' +
                ", exitStationCode='" + exitStationCode + '\'' +
                ", ticketPrice=" + ticketPrice +
                ", singelTicketNum=" + singelTicketNum +
                ", singleTicketType=" + singleTicketType +
                ", channelCode='" + channelCode + '\'' +
                ", orderStatus=" + orderStatus +
                ", collectStatus=" + collectStatus +
                ", deviceId='" + deviceId + '\'' +
                ", actualTakeTicketNum=" + actualTakeTicketNum +
                ", errorCode='" + errorCode + '\'' +
                '}';
    }
}
