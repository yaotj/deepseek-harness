package com.chinasofti.huateng.para.entity.risk;

import java.time.LocalDateTime;

public class RiskControlLog {
    private Long logId;
    private String cardId;
    private String userOrderNo;
    private String ruleId;
    private String ruleName;
    private String ruleDesc;
    private Integer riskLevel;
    private LocalDateTime riskHitTime;
    private String rsikStatus;
    private LocalDateTime riskHanleDate;
    private String riskHanleDesc;
    private String riskHanleManagerId;
    private String riskDeviceId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    // Getters and Setters
    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getUserOrderNo() { return userOrderNo; }
    public void setUserOrderNo(String userOrderNo) { this.userOrderNo = userOrderNo; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getRuleDesc() { return ruleDesc; }
    public void setRuleDesc(String ruleDesc) { this.ruleDesc = ruleDesc; }
    public Integer getRiskLevel() { return riskLevel; }
    public void setRiskLevel(Integer riskLevel) { this.riskLevel = riskLevel; }
    public LocalDateTime getRiskHitTime() { return riskHitTime; }
    public void setRiskHitTime(LocalDateTime riskHitTime) { this.riskHitTime = riskHitTime; }
    public String getRsikStatus() { return rsikStatus; }
    public void setRsikStatus(String rsikStatus) { this.rsikStatus = rsikStatus; }
    public LocalDateTime getRiskHanleDate() { return riskHanleDate; }
    public void setRiskHanleDate(LocalDateTime riskHanleDate) { this.riskHanleDate = riskHanleDate; }
    public String getRiskHanleDesc() { return riskHanleDesc; }
    public void setRiskHanleDesc(String riskHanleDesc) { this.riskHanleDesc = riskHanleDesc; }
    public String getRiskHanleManagerId() { return riskHanleManagerId; }
    public void setRiskHanleManagerId(String riskHanleManagerId) { this.riskHanleManagerId = riskHanleManagerId; }
    public String getRiskDeviceId() { return riskDeviceId; }
    public void setRiskDeviceId(String riskDeviceId) { this.riskDeviceId = riskDeviceId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
