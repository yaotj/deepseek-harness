package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 跨域同步状态机。当前载体 {@code USER_PHONE_CHANGE_LOG.SIGN_SYNC_STATUS}
 */
public enum SyncStatus {

    /** 待同步。落库即为此态，等补偿扫表或首次同步处理。 */
    PENDING,

    /** 同步成功。唯一终态，扫表不再捞取。 */
    SUCCESS,

    /** 同步失败。可被补偿重推，重试次数达上限后留在此态等人工介入（转异常工单）。 */
    FAILED;

    private static final Map<SyncStatus, Set<SyncStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(SUCCESS, FAILED),
            FAILED, EnumSet.of(SUCCESS, FAILED),
            SUCCESS, EnumSet.noneOf(SyncStatus.class));

    /**
     * 宽松解析：NULL / 空串 / 未知值一律返回 {@code null}，由调用方决定跳过还是拒绝。
     */
    public static SyncStatus parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (SyncStatus s : values()) {
            if (s.name().equals(raw)) {
                return s;
            }
        }
        return null;
    }

    /** 是否会被补偿扫表捞取。与扫表 SQL 的 {@code IN ('PENDING','FAILED')} 必须保持一致。 */
    public boolean isCompensable() {
        return this == PENDING || this == FAILED;
    }

    /** 只做快速失败与错误提示。 */
    public boolean canTransitTo(SyncStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** 无任何合法后继即终态。当前只有 {@code SUCCESS}。 */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
