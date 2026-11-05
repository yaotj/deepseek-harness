package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

public class F2fRefund {

    private Long id;

    private String refundNo;

    private String origOrderNo;

    private String ticketLogicNum;

    private String refundSource;

    private String refundStatus;

    private Integer refundNum;

    private Long refundAmount;

    private String refundReason;

    private String operatorId;

    private String deviceId;

    private String transType;

    private String origTransDate;

    private String payCenterRefundNo;

    private Integer retryTimes;

    private String failReason;

    private LocalDateTime requestTms;

    private LocalDateTime nextQueryTms;

    private LocalDateTime finishTms;

    private LocalDateTime createTms;

    private LocalDateTime updateTms;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRefundNo() {
        return refundNo;
    }

    public void setRefundNo(String refundNo) {
        this.refundNo = refundNo;
    }

    public String getOrigOrderNo() {
        return origOrderNo;
    }

    public void setOrigOrderNo(String origOrderNo) {
        this.origOrderNo = origOrderNo;
    }

    public String getTicketLogicNum() {
        return ticketLogicNum;
    }

    public void setTicketLogicNum(String ticketLogicNum) {
        this.ticketLogicNum = ticketLogicNum;
    }

    public String getRefundSource() {
        return refundSource;
    }

    public void setRefundSource(String refundSource) {
        this.refundSource = refundSource;
    }

    public String getRefundStatus() {
        return refundStatus;
    }

    public void setRefundStatus(String refundStatus) {
        this.refundStatus = refundStatus;
    }

    public Integer getRefundNum() {
        return refundNum;
    }

    public void setRefundNum(Integer refundNum) {
        this.refundNum = refundNum;
    }

    public Long getRefundAmount() {
        return refundAmount;
    }

    public void setRefundAmount(Long refundAmount) {
        this.refundAmount = refundAmount;
    }

    public String getRefundReason() {
        return refundReason;
    }

    public void setRefundReason(String refundReason) {
        this.refundReason = refundReason;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    public String getOrigTransDate() {
        return origTransDate;
    }

    public void setOrigTransDate(String origTransDate) {
        this.origTransDate = origTransDate;
    }

    public String getPayCenterRefundNo() {
        return payCenterRefundNo;
    }

    public void setPayCenterRefundNo(String payCenterRefundNo) {
        this.payCenterRefundNo = payCenterRefundNo;
    }

    public Integer getRetryTimes() {
        return retryTimes;
    }

    public void setRetryTimes(Integer retryTimes) {
        this.retryTimes = retryTimes;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public LocalDateTime getRequestTms() {
        return requestTms;
    }

    public void setRequestTms(LocalDateTime requestTms) {
        this.requestTms = requestTms;
    }

    public LocalDateTime getNextQueryTms() {
        return nextQueryTms;
    }

    public void setNextQueryTms(LocalDateTime nextQueryTms) {
        this.nextQueryTms = nextQueryTms;
    }

    public LocalDateTime getFinishTms() {
        return finishTms;
    }

    public void setFinishTms(LocalDateTime finishTms) {
        this.finishTms = finishTms;
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
}
