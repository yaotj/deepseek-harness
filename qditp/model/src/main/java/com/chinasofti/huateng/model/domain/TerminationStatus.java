package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 解约申请状态机。载体 {@code APP_TERMINATION_REQUEST.TERMINATION_STATUS}。
 */
public enum TerminationStatus {

    /** 待处理。APP 申请解约后落库的初始态，等扫表任务查欠费并调支付平台。 */
    PENDING,

    /** 扫描中。已调支付平台请求解约、等回调或主动查询收口，是长期在途状态。 */
    SCANNING,

    /** 解约成功。终态，签约记录已删、账户域通道已清理。 */
    SUCCESS,

    /** 解约失败。 */
    FAILED;

    private static final Map<TerminationStatus, Set<TerminationStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(SCANNING, FAILED),
            SCANNING, EnumSet.of(SUCCESS, FAILED, PENDING),
            FAILED, EnumSet.of(PENDING),
            SUCCESS, EnumSet.noneOf(TerminationStatus.class));

    /**
     * 宽松解析：库内可能存在 NULL 或历史脏值，解析不出来时返回 {@code null} 而不抛异常。
     */
    public static TerminationStatus parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (TerminationStatus s : values()) {
            if (s.name().equals(raw)) {
                return s;
            }
        }
        return null;
    }

    /** 只做快速失败与错误提示。 */
    public boolean canTransitTo(TerminationStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * 无任何合法后继即终态。当前只有 {@code SUCCESS}；{@code FAILED} 不是终态。
     */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    /**
     * 是否已有可发出的解约结果通知。与 {@code selectCompensableNotify} 的。
     */
    public boolean isNotifiable() {
        return this == SUCCESS || this == FAILED;
    }
}
