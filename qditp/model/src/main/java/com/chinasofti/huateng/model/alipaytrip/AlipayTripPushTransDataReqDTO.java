package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;


public class AlipayTripPushTransDataReqDTO {


    private String thirdUserId;


    private String trxType;


    private String ticketStatus;


    private String signChannelCode;


    private String cardId;


    private String cardType;


    private String handleDateTime;


    private String handleStationCode;


    private String trxAmount;


    private String overtimeAmount;


    private String lastTicketStatus;


    private String lastHandleStationCode;


    private String lastHandleDateTime;


    private String tikcetTransSeq;


    private String deviceId;


    private String channelType;


    private String companionFlag;


    private String countingFlag;


    private String transData;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getTrxType() {
        return trxType;
    }

    public void setTrxType(String trxType) {
        this.trxType = trxType;
    }

    public String getTicketStatus() {
        return ticketStatus;
    }

    public void setTicketStatus(String ticketStatus) {
        this.ticketStatus = ticketStatus;
    }

    public String getSignChannelCode() {
        return signChannelCode;
    }

    public void setSignChannelCode(String signChannelCode) {
        this.signChannelCode = signChannelCode;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getHandleDateTime() {
        return handleDateTime;
    }

    public void setHandleDateTime(String handleDateTime) {
        this.handleDateTime = handleDateTime;
    }

    public String getHandleStationCode() {
        return handleStationCode;
    }

    public void setHandleStationCode(String handleStationCode) {
        this.handleStationCode = handleStationCode;
    }

    public String getTrxAmount() {
        return trxAmount;
    }

    public void setTrxAmount(String trxAmount) {
        this.trxAmount = trxAmount;
    }

    public String getOvertimeAmount() {
        return overtimeAmount;
    }

    public void setOvertimeAmount(String overtimeAmount) {
        this.overtimeAmount = overtimeAmount;
    }

    public String getLastTicketStatus() {
        return lastTicketStatus;
    }

    public void setLastTicketStatus(String lastTicketStatus) {
        this.lastTicketStatus = lastTicketStatus;
    }

    public String getLastHandleStationCode() {
        return lastHandleStationCode;
    }

    public void setLastHandleStationCode(String lastHandleStationCode) {
        this.lastHandleStationCode = lastHandleStationCode;
    }

    public String getLastHandleDateTime() {
        return lastHandleDateTime;
    }

    public void setLastHandleDateTime(String lastHandleDateTime) {
        this.lastHandleDateTime = lastHandleDateTime;
    }

    public String getTikcetTransSeq() {
        return tikcetTransSeq;
    }

    public void setTikcetTransSeq(String tikcetTransSeq) {
        this.tikcetTransSeq = tikcetTransSeq;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getCompanionFlag() {
        return companionFlag;
    }

    public void setCompanionFlag(String companionFlag) {
        this.companionFlag = companionFlag;
    }

    public String getCountingFlag() {
        return countingFlag;
    }

    public void setCountingFlag(String countingFlag) {
        this.countingFlag = countingFlag;
    }

    public String getTransData() {
        return transData;
    }

    public void setTransData(String transData) {
        this.transData = transData;
    }
}
