package com.chinasofti.huateng.account.entity;

import java.time.LocalDateTime;

/**
 * USER_PHONE_CHANGE_LOG 用户手机号更换历史记录表实体。
 */
public class UserPhoneChangeLog {
    /**
     * 主键。
     */
    private Long id;

    /**
     * 第三方用户标识。
     */
    private String thirdUserId;

    /**
     * 用户类型：ITP-地铁APP用户，ALIPAY-支付宝用户。
     */
    private String userType;

    /**
     * 旧手机号。
     */
    private String oldMsisdn;

    /**
     * 新手机号。
     */
    private String newMsisdn;

    /**
     * 操作类型：CHANGE_PHONE-更换手机号。
     */
    private String operType;

    /**
     * 操作时间。
     */
    private LocalDateTime operTime;

    /**
     * 操作人/系统。
     */
    private String operator;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 创建时间。
     */
    private LocalDateTime createTms;

    /**
     * 向支付域同步显示账号的投递状态：PENDING-待投递，SUCCESS-已送达，FAILED-投递失败待重试。
     *
     * <p>NULL 表示本行早于 2026-09-11 的改造，补偿扫描 NEVER 捞取。</p>
     */
    private String signSyncStatus;

    /**
     * 投递重试次数，达配置上限后不再扫描、转人工。
     */
    private Integer signSyncRetryCount;

    /**
     * 最近一次投递时间。
     */
    private LocalDateTime signSyncTime;

    /**
     * 最近一次投递的返回码与消息。
     */
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
