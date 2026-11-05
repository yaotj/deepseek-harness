package com.chinasofti.huateng.facepay.api.page;

public class F2fTopupSuspiciousPageVO {

    private String orderNo;

    private String channel;

    private String deviceId;

    private String operatorId;

    private String topupStatus;

    private String optResult;

    private String optResultDesc;

    private String faultSlipSeq;

    private String errorCode;

    private String errorMessage;

    private String processed;

    private Long transAmount;

    private String reportTms;

    private String receiveTms;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getOperatorId() { return operatorId; }
    public void setOperatorId(String operatorId) { this.operatorId = operatorId; }

    public String getTopupStatus() { return topupStatus; }
    public void setTopupStatus(String topupStatus) { this.topupStatus = topupStatus; }

    public String getOptResult() { return optResult; }
    public void setOptResult(String optResult) { this.optResult = optResult; }

    public String getOptResultDesc() { return optResultDesc; }
    public void setOptResultDesc(String optResultDesc) { this.optResultDesc = optResultDesc; }

    public String getFaultSlipSeq() { return faultSlipSeq; }
    public void setFaultSlipSeq(String faultSlipSeq) { this.faultSlipSeq = faultSlipSeq; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public String getProcessed() { return processed; }
    public void setProcessed(String processed) { this.processed = processed; }

    public Long getTransAmount() { return transAmount; }
    public void setTransAmount(Long transAmount) { this.transAmount = transAmount; }

    public String getReportTms() { return reportTms; }
    public void setReportTms(String reportTms) { this.reportTms = reportTms; }

    public String getReceiveTms() { return receiveTms; }
    public void setReceiveTms(String receiveTms) { this.receiveTms = receiveTms; }
}
