package com.chinasofti.huateng.account.entity;

import java.time.LocalDateTime;


public class UserPhoneChangeLog {

    private Long id;


    private String thirdUserId;


    private String userType;


    private String oldMsisdn;


    private String newMsisdn;


    private String operType;


    private LocalDateTime operTime;


    private String operator;


    private String remark;


    private LocalDateTime createTms;


    private String signSyncStatus;


    private Integer signSyncRetryCount;


    private LocalDateTime signSyncTime;


    private String signSyncResult;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getOldMsisdn() {
        return oldMsisdn;
    }

    public void setOldMsisdn(String oldMsisdn) {
        this.oldMsisdn = oldMsisdn;
    }

    public String getNewMsisdn() {
        return newMsisdn;
    }

    public void setNewMsisdn(String newMsisdn) {
        this.newMsisdn = newMsisdn;
    }

    public String getOperType() {
        return operType;
    }

    public void setOperType(String operType) {
        this.operType = operType;
    }

    public LocalDateTime getOperTime() {
        return operTime;
    }

    public void setOperTime(LocalDateTime operTime) {
        this.operTime = operTime;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public String getSignSyncStatus() {
        return signSyncStatus;
    }

    public void setSignSyncStatus(String signSyncStatus) {
        this.signSyncStatus = signSyncStatus;
    }

    public Integer getSignSyncRetryCount() {
        return signSyncRetryCount;
    }

    public void setSignSyncRetryCount(Integer signSyncRetryCount) {
        this.signSyncRetryCount = signSyncRetryCount;
    }

    public LocalDateTime getSignSyncTime() {
        return signSyncTime;
    }

    public void setSignSyncTime(LocalDateTime signSyncTime) {
        this.signSyncTime = signSyncTime;
    }

    public String getSignSyncResult() {
        return signSyncResult;
    }

    public void setSignSyncResult(String signSyncResult) {
        this.signSyncResult = signSyncResult;
    }
}
