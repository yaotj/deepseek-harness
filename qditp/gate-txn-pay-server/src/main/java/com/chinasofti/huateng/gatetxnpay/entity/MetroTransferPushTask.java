package com.chinasofti.huateng.gatetxnpay.entity;

import java.time.LocalDateTime;

/** 公交换乘行程推送 outbox 任务。 */
public class MetroTransferPushTask {
    private Long id;
    private String orderNo;
    private String thirdUserId;
    private String transDate;
    private String transTime;
    private String payChannelType;
    private String transferFlag;
    private String cardType;
    private String status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lastError;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getTransDate() { return transDate; }
    public void setTransDate(String transDate) { this.transDate = transDate; }
    public String getTransTime() { return transTime; }
    public void setTransTime(String transTime) { this.transTime = transTime; }
    public String getPayChannelType() { return payChannelType; }
    public void setPayChannelType(String payChannelType) { this.payChannelType = payChannelType; }
    public String getTransferFlag() { return transferFlag; }
    public void setTransferFlag(String transferFlag) { this.transferFlag = transferFlag; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public LocalDateTime getNextRetryTime() { return nextRetryTime; }
    public void setNextRetryTime(LocalDateTime nextRetryTime) { this.nextRetryTime = nextRetryTime; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
