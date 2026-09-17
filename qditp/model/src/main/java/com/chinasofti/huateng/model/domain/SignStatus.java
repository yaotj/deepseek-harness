package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 通道签约状态机。载体 {@code APP_PAY_SIGN_INFO.SIGN_STATUS}。
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
     * 宽松解析：库内可能存在 NULL 或历史脏值，解析不出来时返回 {@code null} 而不抛异常。
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

    /** 只做快速失败与错误提示。 */
    public boolean canTransitTo(SignStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** 无任何合法后继即终态。当前没有真终态：UNSIGNED / FAILED 都可复位重签。 */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
