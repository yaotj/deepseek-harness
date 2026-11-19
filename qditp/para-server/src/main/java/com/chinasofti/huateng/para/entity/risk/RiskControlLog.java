package com.chinasofti.huateng.para.entity.risk;

import java.time.LocalDateTime;

public class RiskControlLog {
    private Long logId;
    private String cardId;
    private String userOrderNo;
    private String ruleId;
    private String ruleNameSnap;
    private String ruleDescSnap;
    private Integer riskLevelSnap;
    private String riskStatus;
    private LocalDateTime riskHandleTime;
    private String riskHandleDesc;
    private String riskHandleManagerId;
    private String riskDeviceId;
    private LocalDateTime riskHitTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getUserOrderNo() { return userOrderNo; }
    public void setUserOrderNo(String userOrderNo) { this.userOrderNo = userOrderNo; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleNameSnap() { return ruleNameSnap; }
    public void setRuleNameSnap(String ruleNameSnap) { this.ruleNameSnap = ruleNameSnap; }
    public String getRuleDescSnap() { return ruleDescSnap; }
    public void setRuleDescSnap(String ruleDescSnap) { this.ruleDescSnap = ruleDescSnap; }
    public Integer getRiskLevelSnap() { return riskLevelSnap; }
    public void setRiskLevelSnap(Integer riskLevelSnap) { this.riskLevelSnap = riskLevelSnap; }
    public String getRiskStatus() { return riskStatus; }
    public void setRiskStatus(String riskStatus) { this.riskStatus = riskStatus; }
    public LocalDateTime getRiskHandleTime() { return riskHandleTime; }
    public void setRiskHandleTime(LocalDateTime riskHandleTime) { this.riskHandleTime = riskHandleTime; }
    public String getRiskHandleDesc() { return riskHandleDesc; }
    public void setRiskHandleDesc(String riskHandleDesc) { this.riskHandleDesc = riskHandleDesc; }
    public String getRiskHandleManagerId() { return riskHandleManagerId; }
    public void setRiskHandleManagerId(String riskHandleManagerId) { this.riskHandleManagerId = riskHandleManagerId; }
    public String getRiskDeviceId() { return riskDeviceId; }
    public void setRiskDeviceId(String riskDeviceId) { this.riskDeviceId = riskDeviceId; }
    public LocalDateTime getRiskHitTime() { return riskHitTime; }
    public void setRiskHitTime(LocalDateTime riskHitTime) { this.riskHitTime = riskHitTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
