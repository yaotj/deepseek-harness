package com.chinasofti.huateng.blacklist.entity;

import java.time.LocalDateTime;


public class Blacklist {

    private Long id;


    private String cardId;


    private String thirdUserId;


    private String cardType;


    private String channelCode;


    private String blackSource;


    private String blackCause;


    private String bizNo;


    private String reason;


    private String createBy;


    private LocalDateTime createTime;


    private String channelSyncStatus;


    private LocalDateTime channelSyncTime;


    private Integer channelSyncRetry;


    private String channelSyncFailReason;


    private String status;


    private String releaseReason;


    private String releaseBy;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReleaseReason() {
        return releaseReason;
    }

    public void setReleaseReason(String releaseReason) {
        this.releaseReason = releaseReason;
    }

    public String getReleaseBy() {
        return releaseBy;
    }

    public void setReleaseBy(String releaseBy) {
        this.releaseBy = releaseBy;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getChannelCode() {
        return channelCode;
    }

    public void setChannelCode(String channelCode) {
        this.channelCode = channelCode;
    }

    public String getBlackSource() {
        return blackSource;
    }

    public void setBlackSource(String blackSource) {
        this.blackSource = blackSource;
    }

    public String getBlackCause() {
        return blackCause;
    }

    public void setBlackCause(String blackCause) {
        this.blackCause = blackCause;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getChannelSyncStatus() {
        return channelSyncStatus;
    }

    public void setChannelSyncStatus(String channelSyncStatus) {
        this.channelSyncStatus = channelSyncStatus;
    }

    public LocalDateTime getChannelSyncTime() {
        return channelSyncTime;
    }

    public void setChannelSyncTime(LocalDateTime channelSyncTime) {
        this.channelSyncTime = channelSyncTime;
    }

    public Integer getChannelSyncRetry() {
        return channelSyncRetry;
    }

    public void setChannelSyncRetry(Integer channelSyncRetry) {
        this.channelSyncRetry = channelSyncRetry;
    }

    public String getChannelSyncFailReason() {
        return channelSyncFailReason;
    }

    public void setChannelSyncFailReason(String channelSyncFailReason) {
        this.channelSyncFailReason = channelSyncFailReason;
    }
}
