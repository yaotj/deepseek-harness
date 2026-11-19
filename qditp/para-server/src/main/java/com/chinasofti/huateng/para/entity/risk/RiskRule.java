package com.chinasofti.huateng.para.entity.risk;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class RiskRule {
    private String ruleId;

    private String ruleName;

    private String ruleDesc;

    private Integer riskLevel;

    private BigDecimal riskLimitValue;

    private String managerCode;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private List<Long> groupIds;

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public String getRuleDesc() { return ruleDesc; }
    public void setRuleDesc(String ruleDesc) { this.ruleDesc = ruleDesc; }
    public Integer getRiskLevel() { return riskLevel; }
    public void setRiskLevel(Integer riskLevel) { this.riskLevel = riskLevel; }
    public BigDecimal getRiskLimitValue() { return riskLimitValue; }
    public void setRiskLimitValue(BigDecimal riskLimitValue) { this.riskLimitValue = riskLimitValue; }
    public String getManagerCode() { return managerCode; }
    public void setManagerCode(String managerCode) { this.managerCode = managerCode; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public List<Long> getGroupIds() { return groupIds; }
    public void setGroupIds(List<Long> groupIds) { this.groupIds = groupIds; }
}
