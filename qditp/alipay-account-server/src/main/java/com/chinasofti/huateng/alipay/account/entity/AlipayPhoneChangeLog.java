package com.chinasofti.huateng.alipay.account.entity;

import java.time.LocalDateTime;

/**
 * 支付宝渠道用户手机号更换历史记录，对应表 ALIPAY_PHONE_CHANGE_LOG。
 *
 * <p>本表由 alipay-account-server 独占写入，与 account-server 的 USER_PHONE_CHANGE_LOG
 * 是两条互不相干的链路（用户 2026-09-11 裁定）。NEVER 再把两者合表或共用序列。
 */
public class AlipayPhoneChangeLog {
    private Long id;
    private String thirdUserId;
    private String oldMsisdn;
    private String newMsisdn;
    private String operType;
    private LocalDateTime operTime;
    private String operator;
    private String remark;
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
