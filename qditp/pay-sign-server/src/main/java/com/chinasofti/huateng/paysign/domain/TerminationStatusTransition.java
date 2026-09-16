package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.TerminationStatus;

import java.util.function.Supplier;

/**
 * {@code APP_TERMINATION_REQUEST.TERMINATION_STATUS} 的 CAS 迁移结果判定。
 *
 * <p>与 {@link SignStatusTransition} 形态一致、语义独立：本类判解约申请状态机，
 * 那个判通道签约状态机。<b>两者刻意不合并成一个泛型工具</b> —— ADR-D40 已裁决
 * 「NEVER 把它扩成通用状态机引擎」，且合并要动 {@code SignStatusTransition} 已被
 * {@code SignStatusTransitionTest} 与 {@code SignStatusArchTest} 钉住的签名。
 * 这里重复的只是 3 个常量 + 一个 record 的结构，没有重复任何判定逻辑。
 *
 * <p><b>NEVER 在本类里加 CAS 前的白名单校验</b>（即 {@code TerminationStatus.canTransitTo}）：
 * 权威白名单是 mapper 那 6 条 CAS 自己的 WHERE 前置条件，而调用方手里的「当前状态」来自更早
 * 一次 select、随时可能已过期（{@code SCANNING} 是长期在途态，回调与超时扫描会互相抢）。
 * 用过期值提前拦一道只会造成<b>误拦</b>，且比 CAS 返 0 行更难排查。
 *
 * <p>三种结局的处置<b>按调用点不同，刻意不统一</b>：
 * <ul>
 *   <li>{@code receiveTerminationResult}（支付平台回调）：{@code CONFLICT} <b>只告警、不对上游报错</b>
 *       —— 回调返错会引来重推，而重推每次都会在同一处冲突，形成无出口的循环。</li>
 *   <li>{@code notifyFailed}（内部接口）：{@code CONFLICT} 返错拒绝，让调用方知道这条没推动。</li>
 * </ul>
 */
public final class TerminationStatusTransition {

    private TerminationStatusTransition() {
    }

    /** CAS 迁移的三种结局。<b>调用方 MUST 显式处理 {@link #CONFLICT}，NEVER 静默忽略。</b> */
    public enum Outcome {
        /** CAS 命中，状态已按预期变更。 */
        DONE,
        /** CAS 影响 0 行，但回查发现库里已是目标态：上游重放，按成功处理。 */
        IDEMPOTENT,
        /** CAS 影响 0 行且库里不是目标态：这条迁移不被白名单允许，或已被并发改走。 */
        CONFLICT
    }

    /**
     * 判定结果。{@code observedStatus} 只在 CAS 返 0 行时才有值（{@code DONE} 时为 {@code null}），
     * 因为只有那种情况才做过回查 —— 调用方需要它来拼日志或把本地真实状态回给上游。
     */
    public record Result(Outcome outcome, String observedStatus) {

        public boolean isConflict() {
            return outcome == Outcome.CONFLICT;
        }

        public boolean isDone() {
            return outcome == Outcome.DONE;
        }
    }

    /**
     * 判定一次 CAS 的结局。
     *
     * <p><b>{@code currentStatusLoader} 只在 {@code updatedRows == 0} 时被调用</b>，
     * 「CAS 命中就不回查」是本规则的一部分，NEVER 改成先无条件回查再判断 ——
     * 那会给每次成功迁移都加一次多余的 DB 往返。
     *
     * @param target              本次要迁到的目标态
     * @param currentStatusLoader 回查库内当前状态，允许返回 {@code null} 或历史脏值
     */
    public static Result classify(int updatedRows, TerminationStatus target, Supplier<String> currentStatusLoader) {
        if (updatedRows > 0) {
            return new Result(Outcome.DONE, null);
        }
        String raw = currentStatusLoader.get();
        // 解析不出来（NULL / 脏值 / 大小写不符）一律按 CONFLICT，NEVER 兜底成目标态：
        // 猜错方向会把「状态未知」当成「已经成功」，把不一致藏起来。
        TerminationStatus current = TerminationStatus.parseOrNull(raw);
        Outcome outcome = target != null && target == current ? Outcome.IDEMPOTENT : Outcome.CONFLICT;
        return new Result(outcome, raw);
    }
}
