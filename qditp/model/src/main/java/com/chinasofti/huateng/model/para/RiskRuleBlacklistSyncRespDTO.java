package com.chinasofti.huateng.model.para;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 风险规则命中自动加黑（黑名单直拉规则组）单轮执行结果。
 * 调用链：web-admin Quartz → para-server /internal/risk/rule-blacklist-sync。
 */
public class RiskRuleBlacklistSyncRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 命中的风险组名（未配置组时为空串）。 */
    private String groupName;

    /** 本轮扫描的时间窗口（天）。 */
    private Integer windowDays;

    /** 组内参与聚合的规则数。 */
    private int ruleCount;

    /** 窗口内达阈值的候选卡数。 */
    private int candidates;

    /** 本轮实际加黑数。 */
    private int added;

    /** 已在黑名单而跳过数。 */
    private int skipped;

    /** 加黑失败数（非幂等类失败，已逐卡记 error）。 */
    private int failed;

    /** 候选数超过单轮上限被截断数。 */
    private int truncated;

    private List<Detail> details = new ArrayList<>();

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Integer getWindowDays() {
        return windowDays;
    }

    public void setWindowDays(Integer windowDays) {
        this.windowDays = windowDays;
    }

    public int getRuleCount() {
        return ruleCount;
    }

    public void setRuleCount(int ruleCount) {
        this.ruleCount = ruleCount;
    }

    public int getCandidates() {
        return candidates;
    }

    public void setCandidates(int candidates) {
        this.candidates = candidates;
    }

    public int getAdded() {
        return added;
    }

    public void setAdded(int added) {
        this.added = added;
    }

    public int getSkipped() {
        return skipped;
    }

    public void setSkipped(int skipped) {
        this.skipped = skipped;
    }

    public int getFailed() {
        return failed;
    }

    public void setFailed(int failed) {
        this.failed = failed;
    }

    public int getTruncated() {
        return truncated;
    }

    public void setTruncated(int truncated) {
        this.truncated = truncated;
    }

    public List<Detail> getDetails() {
        return details;
    }

    public void setDetails(List<Detail> details) {
        this.details = details;
    }

    public void addDetail(Detail detail) {
        this.details.add(detail);
    }

    /** 单卡处置明细，便于运维核对与审计。 */
    public static class Detail implements Serializable {

        private static final long serialVersionUID = 1L;

        private String cardId;
        private String ruleId;
        private String ruleName;
        private int hitCount;
        /** ADDED / SKIPPED_EXISTING / FAILED / TRUNCATED */
        private String action;
        private String resultMsg;

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

        public int getHitCount() {
            return hitCount;
        }

        public void setHitCount(int hitCount) {
            this.hitCount = hitCount;
        }

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getResultMsg() {
            return resultMsg;
        }

        public void setResultMsg(String resultMsg) {
            this.resultMsg = resultMsg;
        }
    }
}
