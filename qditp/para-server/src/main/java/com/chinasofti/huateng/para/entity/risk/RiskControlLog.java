package com.chinasofti.huateng.para.entity.risk;

import java.time.LocalDateTime;

public class RiskControlLog {
    private Long logId;

    private String cardId;

    private String userOrderNo;

    private String ruleId;

    private String ruleName;

    private LocalDateTime riskHitTime;

    private LocalDateTime createTime;

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
    public LocalDateTime getRiskHitTime() { return riskHitTime; }
    public void setRiskHitTime(LocalDateTime riskHitTime) { this.riskHitTime = riskHitTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
