package com.chinasofti.huateng.para.entity.risk;

import java.sql.Timestamp;

public class GraylistRisk {
    private Long id;
    private String cardId;
    private String ruleName;
    private Integer riskTimes;
    private Timestamp riskLastTime;
    private String managerId;
    private Timestamp updateTime;
    private Timestamp regTime;
    private String migrateBatch;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public Integer getRiskTimes() { return riskTimes; }
    public void setRiskTimes(Integer riskTimes) { this.riskTimes = riskTimes; }
    public Timestamp getRiskLastTime() { return riskLastTime; }
    public void setRiskLastTime(Timestamp riskLastTime) { this.riskLastTime = riskLastTime; }
    public String getManagerId() { return managerId; }
    public void setManagerId(String managerId) { this.managerId = managerId; }
    public Timestamp getUpdateTime() { return updateTime; }
    public void setUpdateTime(Timestamp updateTime) { this.updateTime = updateTime; }
    public Timestamp getRegTime() { return regTime; }
    public void setRegTime(Timestamp regTime) { this.regTime = regTime; }
    public String getMigrateBatch() { return migrateBatch; }
    public void setMigrateBatch(String migrateBatch) { this.migrateBatch = migrateBatch; }
}
