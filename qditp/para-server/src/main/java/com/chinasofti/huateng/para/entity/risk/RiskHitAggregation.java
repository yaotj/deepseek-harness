package com.chinasofti.huateng.para.entity.risk;

/**
 * 风险命中窗口聚合结果：同卡同规则在窗口期内的命中次数。
 * 仅 para-server 内部使用，不跨服务共享。
 */
public class RiskHitAggregation {

    private String cardId;
    private String ruleId;
    private String ruleName;
    private Integer hitCount;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public Integer getHitCount() {
        return hitCount;
    }

    public void setHitCount(Integer hitCount) {
        this.hitCount = hitCount;
    }
}
