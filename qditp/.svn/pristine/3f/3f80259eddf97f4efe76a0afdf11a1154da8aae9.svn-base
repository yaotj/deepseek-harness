package com.chinasofti.huateng.para.entity.risk;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 风险阈值规则定义；规则编号是稳定的业务主键。 */
public class RiskRule {
    /** 运营人员维护的风险规则编号。 */
    private String ruleId;

    /** 可选的所属风险组主键。 */
    private Long groupId;

    /** 关联查询得到的风险组名称，不直接落规则表。 */
    private String groupName;

    /** 风险规则显示名称。 */
    private String ruleName;

    /** 风险等级，限定为1至5。 */
    private Integer riskLevel;

    /** 规则触发阈值或最大值。 */
    private BigDecimal riskLimitValue;

    /** 外部或内部管理编码。 */
    private String managerCode;

    /** 运营备注。 */
    private String remark;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 最后修改时间。 */
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
