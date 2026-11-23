package com.chinasofti.huateng.model.para;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 风控命中批量派生（方案 B，IT-086）单轮执行结果。
 * 调用链：web-admin Quartz(sys_job 426) → para-server /internal/risk/hit-derive。
 * 派生口径（2026-11-19 用户裁定）：
 *   000003 最大允许单边交易数 —— 超时出站(出站明细 OVERTIME_AMOUNT 大于 0)与滞留进站(04 超 N 小时)两种都算；
 *   000002 出站规则 —— 同一自然日出站明细(TRX_TYPE 02/03) ≥ 阈值记一次命中。
 */
public class RiskHitDeriveRespDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 派生目标风险组名（未配置组时为空串）。 */
    private String groupName;

    /** 扫描时间窗口（天）。 */
    private Integer windowDays;

    /** 组内参与派生的规则数。 */
    private int ruleCount;

    /** 本轮派生出的候选命中总数（含重复）。 */
    private int candidates;

    /** 实际写入 TBL_RISK_CONTROL_LOG 的行数。 */
    private int inserted;

    /** 因既有行去重跳过的行数。 */
    private int duplicates;

    private List<RuleDetail> ruleDetails = new ArrayList<>();

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

    public int getInserted() {
        return inserted;
    }

    public void setInserted(int inserted) {
        this.inserted = inserted;
    }

    public int getDuplicates() {
        return duplicates;
    }

    public void setDuplicates(int duplicates) {
        this.duplicates = duplicates;
    }

    public List<RuleDetail> getRuleDetails() {
        return ruleDetails;
    }

    public void setRuleDetails(List<RuleDetail> ruleDetails) {
        this.ruleDetails = ruleDetails;
    }

    public void addRuleDetail(RuleDetail detail) {
        this.ruleDetails.add(detail);
    }

    /** 单规则派生明细，便于运维核对。 */
    public static class RuleDetail implements Serializable {

        private static final long serialVersionUID = 1L;

        private String ruleId;
        private String ruleName;
        /** 派生子类口径说明（如 超时出站/滞留进站/自然日重复出站）。 */
        private String kind;
        private int candidates;
        private int inserted;
        private int duplicates;

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

        public String getKind() {
            return kind;
        }

        public void setKind(String kind) {
            this.kind = kind;
        }

        public int getCandidates() {
            return candidates;
        }

        public void setCandidates(int candidates) {
            this.candidates = candidates;
        }

        public int getInserted() {
            return inserted;
        }

        public void setInserted(int inserted) {
            this.inserted = inserted;
        }

        public int getDuplicates() {
            return duplicates;
        }

        public void setDuplicates(int duplicates) {
            this.duplicates = duplicates;
        }
    }
}
