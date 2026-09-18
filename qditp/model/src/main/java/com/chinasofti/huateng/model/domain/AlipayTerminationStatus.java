package com.chinasofti.huateng.model.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 支付宝出行解约登记状态机。载体 {@code ALIPAY_TERMINATION_REQUEST.STATUS}。
 *
 * <p>迁移图：</p>
 * <pre>
 *   PENDING ──→ COMPLETED   （销卡执行成功，唯一终态）
 *   PENDING ──→ FAIL        （签约信息不存在等明确失败）
 * </pre>
 *
 * <p>注意这里的终态是 {@code COMPLETED} 和 {@code FAIL}，两者都不可复活。
 * 与 pay-sign 侧 {@link TerminationStatus} 的区别：pay-sign 有 {@code SCANNING} 中间态
 * 与 {@code FAILED -> PENDING} 复活路径；本渠道没有扫描阶段，执行成功直接 {@code COMPLETED}，
 * 失败即 {@code FAIL}。</p>
 *
 * <p><b>NEVER 与 {@link TerminationStatus} 归一</b>（ADR-D124）：两者的词表不同 ——
 * pay-sign 侧 {@code PENDING / SCANNING / SUCCESS / FAILED}，
 * 本渠道 {@code PENDING / COMPLETED / FAIL}。拼写差异（{@code FAIL} vs {@code FAILED}、
 * {@code COMPLETED} vs {@code SUCCESS}）是各自的数据库现状，强行统一只会写出库里不存在的值。</p>
 */
public enum AlipayTerminationStatus {

    /** 待处理。销卡批处理按此状态捞取，执行器按条推进。 */
    PENDING,

    /** 销卡完成。终态，NEVER 回到 PENDING。 */
    COMPLETED,

    /** 明确失败。终态，NEVER 回到 PENDING —— 签约信息不存在这类，重试多少次也不会自愈。 */
    FAIL;

    private static final Map<AlipayTerminationStatus, Set<AlipayTerminationStatus>> ALLOWED = Map.of(
            PENDING, EnumSet.of(COMPLETED, FAIL),
            COMPLETED, EnumSet.noneOf(AlipayTerminationStatus.class),
            FAIL, EnumSet.noneOf(AlipayTerminationStatus.class));

    /** 宽松解析：库内可能是 NULL 或历史脏值，解析不出来返回 {@code null} 而不抛异常。 */
    public static AlipayTerminationStatus parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (AlipayTerminationStatus status : values()) {
            if (status.name().equals(raw)) {
                return status;
            }
        }
        return null;
    }

    /** 只做快速失败与错误提示，NEVER 当作并发保证 —— 并发保证是 mapper 里 CAS 的 WHERE。 */
    public boolean canTransitTo(AlipayTerminationStatus target) {
        return target != null && ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /** 无任何合法后继即终态。 */
    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }

    /** 批处理捞取条件：只有 PENDING 才需要执行。与扫表 SQL 的 {@code STATUS = 'PENDING'} 保持一致。 */
    public boolean isActionable() {
        return this == PENDING;
    }
}
