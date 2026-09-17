package com.chinasofti.huateng.facepay.domain;

import java.util.function.Supplier;

/** {@code F2F_ORDER.ORDER_STATUS} 的 CAS 结果解读器。 */
public final class F2fOrderStatusTransition {

    private F2fOrderStatusTransition() {
    }

    /** CAS 迁移的三种结局。 */
    public enum Outcome {
        /** CAS 命中，状态已按预期推进。 */
        DONE,
        /** CAS 未命中，但库内已是目标态 —— 重复上报 / 重复回调，按成功处理。 */
        IDEMPOTENT,
        /** CAS 未命中且库内不是目标态 —— 状态被别人推走了，或前置状态本就不满足。 */
        CONFLICT
    }

    /** 判定结果。 */
    public record Result(Outcome outcome, String observedStatus) {

        public boolean conflict() {
            return outcome == Outcome.CONFLICT;
        }

        /** {@code DONE} 与 {@code IDEMPOTENT} 都算「状态已到位」，调用方可以继续往下走。 */
        public boolean settled() {
            return outcome != Outcome.CONFLICT;
        }
    }

    /**
     * 解读 CAS 结果。
     *
     * @param updatedRows         CAS 的影响行数
     * @param target              本次要推进到的目标态，允许 {@code null}（那时只会得到 CONFLICT）
     * @param currentStatusLoader 回查库内当前状态，允许返回 {@code null} 或历史脏值
     */
    public static Result classify(int updatedRows, F2fOrderStatus target,
                                  Supplier<String> currentStatusLoader) {
        if (updatedRows > 0) {
            return new Result(Outcome.DONE, null);
        }
        String raw = currentStatusLoader.get();
        F2fOrderStatus current = F2fOrderStatus.parseOrNull(raw);
        Outcome outcome = target != null && target == current ? Outcome.IDEMPOTENT : Outcome.CONFLICT;
        return new Result(outcome, raw);
    }
}
