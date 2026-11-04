package com.chinasofti.huateng.key.constant;

import java.util.Map;
import java.util.Set;

/**
 * {@code METRO_AGM_KEY_VERSION.PUBLIC_KEY_VERSION_STATUS} 的审批状态机。
 *
 * <p>库内取值：{@code 20010 初始化 / 20020 审批通过 / 20030 拒绝}。审批是密钥域的操作型动作，
 * 属安全红线（AGENTS.md §4.2）：只允许 {@link #INIT} 单向迁到 {@link #APPROVED} / {@link #REJECTED}，
 * 终态不可再迁；迁移用数据库 CAS（WHERE 带当前状态）兜底并发，白名单是前置快速失败、
 * <b>不是并发保证</b>。
 *
 * <p>本表在仓库内原本无代码写路径（审批走库外人工 / 业主侧），本枚举是综管台补齐操作型入口
 * 时<b>刻意</b>收窄成的白名单：只放行 INIT 的两个出边，终态零出边。
 */
public enum AgmKeyApprovalStatus {

    /** 初始化：版本已录入，等待审批。 */
    INIT("20010"),
    /** 审批通过。 */
    APPROVED("20020"),
    /** 拒绝。 */
    REJECTED("20030");

    private final String code;

    AgmKeyApprovalStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 迁移白名单：只放行 INIT 的两个出边，终态（APPROVED / REJECTED）零出边。 */
    private static final Map<AgmKeyApprovalStatus, Set<AgmKeyApprovalStatus>> ALLOWED = Map.of(
            INIT, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(),
            REJECTED, Set.of());

    /** 宽松解析：库内脏值 / 未识别取值返 null，NEVER 抛异常。 */
    public static AgmKeyApprovalStatus parseByCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        for (AgmKeyApprovalStatus st : values()) {
            if (st.code.equals(trimmed)) {
                return st;
            }
        }
        return null;
    }

    /** 前置快速失败 + 错误提示用，NEVER 当并发保证（并发由数据库 CAS 兜底）。 */
    public boolean canTransitTo(AgmKeyApprovalStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** 终态即无出边。 */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
