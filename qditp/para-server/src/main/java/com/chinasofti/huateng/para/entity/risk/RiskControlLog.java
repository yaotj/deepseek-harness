package com.chinasofti.huateng.para.entity.risk;

import java.time.LocalDateTime;

/** 风险规则命中审计记录，仅供运营端查询。 */
public class RiskControlLog {
    /** 数据库生成的审计记录主键。 */
    private Long logId;

    /** 命中规则时关联的逻辑卡号。 */
    private String cardId;

    /** 命中规则时关联的用户订单号。 */
    private String userOrderNo;

    /** 触发风险的规则编号。 */
    private String ruleId;

    /** 左关联当前规则表得到的规则名称，规则删除后可能为空。 */
    private String ruleName;

    /** 风险发生或最后一次命中时间。 */
    private LocalDateTime riskHitTime;

    /** 审计记录入库时间。 */
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
