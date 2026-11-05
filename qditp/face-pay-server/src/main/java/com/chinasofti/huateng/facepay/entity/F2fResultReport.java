package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

public class F2fResultReport {

    private Long id;

    private String reportType;

    private String orderNo;

    private String channel;

    private String deviceId;

    private String operatorId;

    private Integer orderTicketNum;

    private Integer actualTicketNum;

    private String topupStatus;

    private String optResult;

    private String optResultDesc;

    private String faultSlipSeq;

    private String errorCode;

    private String errorMessage;

    private Long transAmount;

    private Long afterAmount;

    private String reportTms;

    private LocalDateTime receiveTms;

    private String processed;

    private String rawBody;

    private LocalDateTime createTms;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
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

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public Integer getOrderTicketNum() {
        return orderTicketNum;
    }

    public void setOrderTicketNum(Integer orderTicketNum) {
        this.orderTicketNum = orderTicketNum;
    }

    public Integer getActualTicketNum() {
        return actualTicketNum;
    }

    public void setActualTicketNum(Integer actualTicketNum) {
        this.actualTicketNum = actualTicketNum;
    }
    public String getTopupStatus() {
        return topupStatus;
    }

    public void setTopupStatus(String topupStatus) {
        this.topupStatus = topupStatus;
    }

    public String getOptResult() {
        return optResult;
    }

    public void setOptResult(String optResult) {
        this.optResult = optResult;
    }

    public String getOptResultDesc() {
        return optResultDesc;
    }

    public void setOptResultDesc(String optResultDesc) {
        this.optResultDesc = optResultDesc;
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
    public Long getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(Long transAmount) {
        this.transAmount = transAmount;
    }

    public Long getAfterAmount() {
        return afterAmount;
    }

    public void setAfterAmount(Long afterAmount) {
        this.afterAmount = afterAmount;
    }

    public String getReportTms() {
        return reportTms;
    }

    public void setReportTms(String reportTms) {
        this.reportTms = reportTms;
    }

    public LocalDateTime getReceiveTms() {
        return receiveTms;
    }

    public void setReceiveTms(LocalDateTime receiveTms) {
        this.receiveTms = receiveTms;
    }

    public String getProcessed() {
        return processed;
    }

    public void setProcessed(String processed) {
        this.processed = processed;
    }

    public String getRawBody() {
        return rawBody;
    }

    public void setRawBody(String rawBody) {
        this.rawBody = rawBody;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }
}
