package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 通道签约状态机。载体 {@code APP_PAY_SIGN_INFO.SIGN_STATUS}。
 * <p>
 * 流转（白名单）：
 * <pre>
 *   NOT_SIGNED -&gt; SIGNED | FAILED
 *   FAILED     -&gt; SIGNED | NOT_SIGNED
 *   SIGNED     -&gt; UNSIGNED
 *   UNSIGNED   -&gt; NOT_SIGNED（复位重签）
 * </pre>
 * <b>NEVER 允许 {@code UNSIGNED -> SIGNED}</b>：已解约通道被迟到的签约回调覆盖，
 * 会让 APP 显示通道有效而渠道侧协议已注销。
 * <p>
 * 本枚举<b>只做快速失败与错误提示，NEVER 当作并发保证</b>。
 * 并发保证唯一来自 {@code PaySignInfoMapper} 的 4 条 CAS UPDATE
 * （{@code markSigned} / {@code markSignFailed} / {@code markUnsigned} / {@code reactivateForResign}），
 * 前置状态写在 SQL 的 WHERE 里。规范见 {@code docs/domain/state-machines.md} §二。
 * <p>
 * <b>NEVER 改动这些常量的字面量</b>：库内已有存量数据按这些字符串存储，
 * 且 pay-sign-server 侧仍有 {@code private static final String} 常量与裸字面量在比较同一批值，
 * 改名等于制造两套口径。新增取值 MUST 同步 {@code ALLOWED} 与 mapper XML 的 CAS。
 */
public enum SignStatus {

    /** 未签约。签约记录刚创建、或解约后复位重签的起点。 */
    NOT_SIGNED,

    /** 已签约。渠道侧协议有效，是免密扣款与解约的唯一合法前置态。 */
    SIGNED,

    /** 已解约。终态之一，只能经 {@code reactivateForResign} 复位为 NOT_SIGNED。 */
    UNSIGNED,

    /** 签约失败。可重签（-&gt; SIGNED）或复位（-&gt; NOT_SIGNED）。 */
    FAILED;

    private static final Map<SignStatus, Set<SignStatus>> ALLOWED = Map.of(
            NOT_SIGNED, EnumSet.of(SIGNED, FAILED),
            FAILED, EnumSet.of(SIGNED, NOT_SIGNED),
            SIGNED, EnumSet.of(UNSIGNED),
            UNSIGNED, EnumSet.of(NOT_SIGNED));

    /**
     * 宽松解析：库内可能存在 NULL 或历史脏值，解析不出来时返回 {@code null} 而不抛异常，
     * 由调用方决定是拒绝还是当未知态跳过。<b>NEVER 在这里兜底成某个具体状态</b> ——
     * 猜错方向会把脏数据推进状态机。
     */
    public static SignStatus parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (SignStatus s : values()) {
            if (s.name().equals(raw)) {
                return s;
            }
        }
        return null;
    }

    /** 只做快速失败与错误提示，NEVER 当作并发保证。并发保证是 mapper 的 CAS。 */
    public boolean canTransitTo(SignStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** 无任何合法后继即终态。当前没有真终态：UNSIGNED / FAILED 都可复位重签。 */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
