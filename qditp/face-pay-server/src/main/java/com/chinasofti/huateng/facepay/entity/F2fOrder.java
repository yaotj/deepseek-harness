package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

public class F2fOrder {

    private Long id;

    private String orderNo;

    private String channel;

    private String bizType;

    private String transType;

    private String adminTransType;

    private String orderStatus;

    private Long orderAmount;

    private String refundStatus;

    private Long refundAmount;

    private LocalDateTime lastRefundTms;

    private String deviceId;

    private String deviceSeq;

    private String stationCode;

    private String operatorId;

    private String shiftId;

    private String thirdUserId;

    private String cardId;

    private String ticketPhysicsNum;

    private Integer ticketNum;

    private Long ticketPrice;

    private String singleTicketType;

    private String entryStationCode;

    private String exitStationCode;

    private Long cardBeforeAmount;

    private Long cardAfterAmount;

    private String suspectSlipNo;

    private String qrcodeGenDate;

    private String randomFact;

    private String activateFlag;

    private String activateDeviceId;

    private LocalDateTime activateTms;

    private LocalDateTime expireTms;

    private LocalDateTime paidTms;

    private LocalDateTime fulfillTms;

    private LocalDateTime createTms;

    private LocalDateTime updateTms;

    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    public String getAdminTransType() {
        return adminTransType;
    }

    public void setAdminTransType(String adminTransType) {
        this.adminTransType = adminTransType;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public Long getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(Long orderAmount) {
        this.orderAmount = orderAmount;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }

    public Long getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Long refundAmount) {
        this.refundAmount = refundAmount;
    }

    public LocalDateTime getLastRefundTms() {
        return lastRefundTms;
    }

    public void setLastRefundTms(LocalDateTime lastRefundTms) {
        this.lastRefundTms = lastRefundTms;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceSeq() {
        return deviceSeq;
    }

    public void setDeviceSeq(String deviceSeq) {
        this.deviceSeq = deviceSeq;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getShiftId() {
        return shiftId;
    }

    public void setShiftId(String shiftId) {
        this.shiftId = shiftId;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getTicketPhysicsNum() {
        return ticketPhysicsNum;
    }

    public void setTicketPhysicsNum(String ticketPhysicsNum) {
        this.ticketPhysicsNum = ticketPhysicsNum;
    }

    public Integer getTicketNum() {
        return ticketNum;
    }

    public void setTicketNum(Integer ticketNum) {
        this.ticketNum = ticketNum;
    }

    public Long getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Long ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getSingleTicketType() {
        return singleTicketType;
    }

    public void setSingleTicketType(String singleTicketType) {
        this.singleTicketType = singleTicketType;
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

    public Long getCardBeforeAmount() {
        return cardBeforeAmount;
    }

    public void setCardBeforeAmount(Long cardBeforeAmount) {
        this.cardBeforeAmount = cardBeforeAmount;
    }

    public Long getCardAfterAmount() {
        return cardAfterAmount;
    }

    public void setCardAfterAmount(Long cardAfterAmount) {
        this.cardAfterAmount = cardAfterAmount;
    }

    public String getSuspectSlipNo() {
        return suspectSlipNo;
    }

    public void setSuspectSlipNo(String suspectSlipNo) {
        this.suspectSlipNo = suspectSlipNo;
    }

    public String getQrcodeGenDate() {
        return qrcodeGenDate;
    }

    public void setQrcodeGenDate(String qrcodeGenDate) {
        this.qrcodeGenDate = qrcodeGenDate;
    }

    public String getRandomFact() {
        return randomFact;
    }

    public void setRandomFact(String randomFact) {
        this.randomFact = randomFact;
    }

    public String getActivateFlag() {
        return activateFlag;
    }

    public void setActivateFlag(String activateFlag) {
        this.activateFlag = activateFlag;
    }

    public String getActivateDeviceId() {
        return activateDeviceId;
    }

    public void setActivateDeviceId(String activateDeviceId) {
        this.activateDeviceId = activateDeviceId;
    }

    public LocalDateTime getActivateTms() {
        return activateTms;
    }

    public void setActivateTms(LocalDateTime activateTms) {
        this.activateTms = activateTms;
    }

    public LocalDateTime getExpireTms() {
        return expireTms;
    }

    public void setExpireTms(LocalDateTime expireTms) {
        this.expireTms = expireTms;
    }

    public LocalDateTime getPaidTms() {
        return paidTms;
    }

    public void setPaidTms(LocalDateTime paidTms) {
        this.paidTms = paidTms;
    }

    public LocalDateTime getFulfillTms() {
        return fulfillTms;
    }

    public void setFulfillTms(LocalDateTime fulfillTms) {
        this.fulfillTms = fulfillTms;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public LocalDateTime getUpdateTms() {
        return updateTms;
    }

    public void setUpdateTms(LocalDateTime updateTms) {
        this.updateTms = updateTms;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
