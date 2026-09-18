package com.chinasofti.huateng.alipay.paysign.domain;

import com.chinasofti.huateng.model.domain.AlipaySignStatus;

import java.util.function.Supplier;

/**
 * {@code ALIPAY_SIGN_INFO.SIGN_STATUS} 的 CAS 迁移结果判定（ADR-D130）。
 *
 * <p>形态照 pay-sign 侧 {@code SignStatusTransition}，三条 NEVER 同样适用：</p>
 * <ol>
 *   <li>{@code currentStatusLoader} <b>只在 {@code updatedRows == 0} 时调用</b> ——
 *       CAS 命中就不回查，这是规则的一部分，不是优化。</li>
 *   <li><b>NEVER 在本类里加 CAS 前的白名单校验</b>：权威白名单是那条 UPDATE 的 WHERE。
 *       调用方手里的「当前状态」来自更早一次 select、随时可能过期，提前拦只会误拦。</li>
 *   <li><b>NEVER 扩成通用状态机引擎</b>：每条 CAS 各带不同的副作用列
 *       （{@code TERMINATION_TIME} / {@code UPDATE_TIME}），归一成 {@code transit(key, from, to)}
 *       会把这些列丢掉。</li>
 * </ol>
 */
public final class AlipaySignStatusTransition {

    private AlipaySignStatusTransition() {
    }

    /** CAS 迁移的三种结局。调用方 MUST 显式处理 {@link #CONFLICT}，NEVER 静默忽略。 */
    public enum Outcome {
        /** CAS 命中，状态已按预期变更。 */
        DONE,
        /** CAS 影响 0 行，但回查发现库里已是目标态：重复执行，按成功处理。 */
        IDEMPOTENT,
        /** CAS 影响 0 行且库里不是目标态：这条迁移不被白名单允许，或数据已被并发改走。 */
        CONFLICT
    }

    /** 判定结果。{@code observedStatus} 只在 CAS 返 0 行时才有值（DONE 时为 {@code null}）。 */
    public record Result(Outcome outcome, String observedStatus) {

        public boolean isConflict() {
            return outcome == Outcome.CONFLICT;
        }

        /** DONE 与 IDEMPOTENT 都算「目标态已达成」，调用方据此决定能不能往下走。 */
        public boolean reachedTarget() {
            return outcome != Outcome.CONFLICT;
        }
    }

    /**
     * 判定一次 CAS 的结局。
     *
     * @param target              本次要迁到的目标态
     * @param currentStatusLoader 回查库内当前状态，允许返回 {@code null} 或历史脏值
     */
    public static Result classify(int updatedRows, AlipaySignStatus target, Supplier<String> currentStatusLoader) {
        if (updatedRows > 0) {
            return new Result(Outcome.DONE, null);
        }
        String raw = currentStatusLoader.get();
        AlipaySignStatus current = AlipaySignStatus.parseOrNull(raw);
        Outcome outcome = target != null && target == current ? Outcome.IDEMPOTENT : Outcome.CONFLICT;
        return new Result(outcome, raw);
    }
}
