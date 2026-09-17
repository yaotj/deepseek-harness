package com.chinasofti.huateng.paysign.entity;

import java.time.LocalDateTime;

public class AppTerminationRequest {
    private Long id;
    private String requestSignSeq;
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String paymentVendor;
    private String terminationStatus;
    private LocalDateTime requestTime;
    private LocalDateTime scanTime;
    private LocalDateTime completeTime;
    private String failReason;
    private String notifyStatus;
    private Integer notifyRetryCount;
    private LocalDateTime notifyTime;
    private String notifyResult;
    /** 「解约成功后清理账户域支付通道」这一动作的投递状态。 */
    private String channelSyncStatus;
    private Integer channelSyncRetryCount;
    private LocalDateTime channelSyncTime;
    private String channelSyncResult;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRequestSignSeq() {
        return requestSignSeq;
    }

    public void setRequestSignSeq(String requestSignSeq) {
        this.requestSignSeq = requestSignSeq;
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

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public String getTerminationStatus() {
        return terminationStatus;
    }

    public void setTerminationStatus(String terminationStatus) {
        this.terminationStatus = terminationStatus;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public void setRequestTime(LocalDateTime requestTime) {
        this.requestTime = requestTime;
    }

    public LocalDateTime getScanTime() {
        return scanTime;
    }

    public void setScanTime(LocalDateTime scanTime) {
        this.scanTime = scanTime;
    }

    public LocalDateTime getCompleteTime() {
        return completeTime;
    }

    public void setCompleteTime(LocalDateTime completeTime) {
        this.completeTime = completeTime;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public String getNotifyStatus() {
        return notifyStatus;
    }

    public void setNotifyStatus(String notifyStatus) {
        this.notifyStatus = notifyStatus;
    }

    public Integer getNotifyRetryCount() {
        return notifyRetryCount;
    }

    public void setNotifyRetryCount(Integer notifyRetryCount) {
        this.notifyRetryCount = notifyRetryCount;
    }

    public LocalDateTime getNotifyTime() {
        return notifyTime;
    }

    public void setNotifyTime(LocalDateTime notifyTime) {
        this.notifyTime = notifyTime;
    }

    public String getNotifyResult() {
        return notifyResult;
    }

    public void setNotifyResult(String notifyResult) {
        this.notifyResult = notifyResult;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getChannelSyncStatus() {
        return channelSyncStatus;
    }

    public void setChannelSyncStatus(String channelSyncStatus) {
        this.channelSyncStatus = channelSyncStatus;
    }

    public Integer getChannelSyncRetryCount() {
        return channelSyncRetryCount;
    }

    public void setChannelSyncRetryCount(Integer channelSyncRetryCount) {
        this.channelSyncRetryCount = channelSyncRetryCount;
    }

    public LocalDateTime getChannelSyncTime() {
        return channelSyncTime;
    }

    public void setChannelSyncTime(LocalDateTime channelSyncTime) {
        this.channelSyncTime = channelSyncTime;
    }

    public String getChannelSyncResult() {
        return channelSyncResult;
    }

    public void setChannelSyncResult(String channelSyncResult) {
        this.channelSyncResult = channelSyncResult;
    }
}
