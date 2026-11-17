package com.chinasofti.huateng.alipay.account.entity;

import java.time.LocalDateTime;


public class AlipayCardPool {


    private String cardId;


    private String thirdUserId;


    private String status;


    private LocalDateTime insertTms;


    private LocalDateTime regTms;


    private String deleteFlag;


    private String version;


    private LocalDateTime createTime;


    private LocalDateTime updateTime;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getInsertTms() {
        return insertTms;
    }

    public void setInsertTms(LocalDateTime insertTms) {
        this.insertTms = insertTms;
    }

    public LocalDateTime getRegTms() {
        return regTms;
    }

    public void setRegTms(LocalDateTime regTms) {
        this.regTms = regTms;
    }

    public String getDeleteFlag() {
        return deleteFlag;
    }

    public void setDeleteFlag(String deleteFlag) {
        this.deleteFlag = deleteFlag;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
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
}
