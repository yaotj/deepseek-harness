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
}
