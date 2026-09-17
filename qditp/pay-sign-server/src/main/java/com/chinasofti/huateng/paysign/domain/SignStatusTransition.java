package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.SignStatus;

import java.util.function.Supplier;

/** {@code APP_PAY_SIGN_INFO.SIGN_STATUS} 的 CAS 迁移结果判定（ADR-D40）。 */
public final class SignStatusTransition {

    private SignStatusTransition() {
    }

    /** CAS 迁移的三种结局。调用方 MUST 显式处理 {@link #CONFLICT}，NEVER 静默忽略。 */
    public enum Outcome {
        /** CAS 命中，状态已按预期变更。 */
        DONE,
        /** CAS 影响 0 行，但回查发现库里已是目标态：上游重放，按成功处理。 */
        IDEMPOTENT,
        /** CAS 影响 0 行且库里不是目标态：这条迁移不被白名单允许，或数据已被并发改走。 */
        CONFLICT
    }

    /** 判定结果。{@code observedStatus} 只在 CAS 返 0 行时才有值（DONE 时为 {@code null}） */
    public record Result(Outcome outcome, String observedStatus) {

        public boolean isConflict() {
            return outcome == Outcome.CONFLICT;
        }
    }

    /**
     * 判定一次 CAS 的结局。
     *
     * @param target             本次要迁到的目标态
     * @param currentStatusLoader 回查库内当前状态，允许返回 {@code null} 或历史脏值
     */
    public static Result classify(int updatedRows, SignStatus target, Supplier<String> currentStatusLoader) {
        if (updatedRows > 0) {
            return new Result(Outcome.DONE, null);
        }
        String raw = currentStatusLoader.get();
        SignStatus current = SignStatus.parseOrNull(raw);
        Outcome outcome = target != null && target == current ? Outcome.IDEMPOTENT : Outcome.CONFLICT;
        return new Result(outcome, raw);
    }
}
