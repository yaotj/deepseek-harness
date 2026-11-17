package com.chinasofti.huateng.para.entity.risk;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RiskRule {
    private String ruleId;

    private Long groupId;

    private String groupName;

    private String ruleName;

    private Integer riskLevel;

    private BigDecimal riskLimitValue;

    private String managerCode;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }
    public Integer getRiskLevel() { return riskLevel; }
    public void setRiskLevel(Integer riskLevel) { this.riskLevel = riskLevel; }
    public BigDecimal getRiskLimitValue() { return riskLimitValue; }
    public void setRiskLimitValue(BigDecimal riskLimitValue) { this.riskLimitValue = riskLimitValue; }
    public String getManagerCode() { return managerCode; }
    public void setManagerCode(String managerCode) { this.managerCode = managerCode; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
