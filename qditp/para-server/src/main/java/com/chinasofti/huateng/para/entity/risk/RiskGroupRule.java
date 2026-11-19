package com.chinasofti.huateng.para.entity.risk;

import java.time.LocalDateTime;

public class RiskGroupRule {
    private Long groupId;
    private String ruleId;
    private LocalDateTime createTime;

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
