package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 跨域同步状态机。当前载体 {@code USER_PHONE_CHANGE_LOG.SIGN_SYNC_STATUS}
 * （手机号变更后把签约展示账号推给支付域），后续新增的「落状态 + 扫表补偿」链路复用同一套取值。
 * <p>
 * 流转（白名单）：
 * <pre>
 *   PENDING -&gt; SUCCESS | FAILED
 *   FAILED  -&gt; SUCCESS | FAILED（重推再失败，仅 RETRY_COUNT+1）
 *   SUCCESS -&gt; 终态
 * </pre>
 * <b>{@code SUCCESS} 是唯一终态，NEVER 允许从它迁出</b>：补偿扫表按
 * {@code SIGN_SYNC_STATUS IN ('PENDING','FAILED')} 捞取，一旦回退就会对已送达的记录重复推送。
 * <p>
 * <b>NULL 不属于本枚举</b>。库内早于改造的历史行该列为 NULL，语义是「不参与本次改造的补偿」，
 * 扫表 SQL 靠 {@code IN} 天然跳过它们。<b>NEVER 把 NULL 兜底成 {@code PENDING}</b> ——
 * 那等于对改造前的存量记录发起一轮真实 RPC 重推。
 * <p>
 * 与 {@link SignStatus} 同理：本枚举只做快速失败，<b>并发保证唯一来自 mapper 的 CAS UPDATE</b>
 * （前置状态写在 WHERE 里 + 重试上限）。规范见 {@code docs/domain/state-machines.md} §二。
 */
public enum SyncStatus {

    /** 待同步。落库即为此态，等补偿扫表或首次同步处理。 */
    PENDING,

    /** 同步成功。<b>唯一终态</b>，扫表不再捞取。 */
    SUCCESS,

    /** 同步失败。可被补偿重推，重试次数达上限后留在此态等人工介入（转异常工单）。 */
    FAILED;

    private static final Map<SyncStatus, Set<SyncStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(SUCCESS, FAILED),
            FAILED, EnumSet.of(SUCCESS, FAILED),
            SUCCESS, EnumSet.noneOf(SyncStatus.class));

    /**
     * 宽松解析：NULL / 空串 / 未知值一律返回 {@code null}，由调用方决定跳过还是拒绝。
     * <b>NEVER 在这里兜底成具体状态</b>，理由见类注释关于 NULL 的说明。
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

    /** 只做快速失败与错误提示，NEVER 当作并发保证。 */
    public boolean canTransitTo(SyncStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** 无任何合法后继即终态。当前只有 {@code SUCCESS}。 */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
