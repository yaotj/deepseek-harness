package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.SignStatus;

import java.util.function.Supplier;

/**
 * {@code APP_PAY_SIGN_INFO.SIGN_STATUS} 的 CAS 迁移结果判定（ADR-D40）。
 *
 * <p><b>本类只做「CAS 之后怎么解读结果」这一件事</b>，是 {@code ContractDomainServiceImpl}
 * 与 {@code PaySignWorkflow} 此前各自手写的那段判定的唯一定义点。
 * 规范见 {@code docs/domain/state-machines.md} §二③。</p>
 *
 * <p><b>NEVER 在本类里加 CAS 前的白名单校验</b>（即 {@code SignStatus.canTransitTo}）：
 * 权威白名单是 4 条 CAS 语句自己的 WHERE 前置条件，而调用方手里的「当前状态」来自更早一次
 * select、随时可能已过期。用过期值提前拦一道只会造成<b>误拦</b>，且比 CAS 返 0 行更难排查。
 * 枚举在本项目的职责就限于解析与文档化，并发保证一律由 SQL 承担。</p>
 *
 * <p><b>NEVER 把本类扩展成「通用状态机引擎」</b>：4 条 CAS 各自带不同的业务副作用列
 * （SIGN_TIME / TERMINATION_TIME / PAY_ACCOUNT_ID / PAY_AGREEMENT_NO），
 * 归一成一条 {@code transit(key, from, to)} 会丢掉这些列，已否决。</p>
 */
public final class SignStatusTransition {

    private SignStatusTransition() {
    }

    /** CAS 迁移的三种结局。<b>调用方 MUST 显式处理 {@link #CONFLICT}，NEVER 静默忽略。</b> */
    public enum Outcome {
        /** CAS 命中，状态已按预期变更。 */
        DONE,
        /** CAS 影响 0 行，但回查发现库里已是目标态：上游重放，按成功处理。 */
        IDEMPOTENT,
        /** CAS 影响 0 行且库里不是目标态：这条迁移不被白名单允许，或数据已被并发改走。 */
        CONFLICT
    }

    /**
     * 判定结果。{@code observedStatus} 只在 CAS 返 0 行时才有值（DONE 时为 {@code null}），
     * 因为只有那种情况才做过回查 —— 调用方需要它来回写「本地真实状态」或拼错误消息。
     */
    public record Result(Outcome outcome, String observedStatus) {

        public boolean isConflict() {
            return outcome == Outcome.CONFLICT;
        }
    }

    /**
     * 判定一次 CAS 的结局。
     *
     * <p><b>{@code currentStatusLoader} 只在 {@code updatedRows == 0} 时被调用</b>，
     * 「CAS 命中就不回查」是本规则的一部分，NEVER 改成先无条件回查再判断 ——
     * 那会给每次成功迁移都加一次多余的 DB 往返。</p>
     *
     * @param target             本次要迁到的目标态
     * @param currentStatusLoader 回查库内当前状态，允许返回 {@code null} 或历史脏值
     */
    public static Result classify(int updatedRows, SignStatus target, Supplier<String> currentStatusLoader) {
        if (updatedRows > 0) {
            return new Result(Outcome.DONE, null);
        }
        String raw = currentStatusLoader.get();
        // 解析不出来（NULL / 脏值 / 大小写不符）一律按 CONFLICT，NEVER 兜底成目标态：
        // 猜错方向会把「状态未知」当成「已经成功」，把不一致藏起来。
        SignStatus current = SignStatus.parseOrNull(raw);
        Outcome outcome = target != null && target == current ? Outcome.IDEMPOTENT : Outcome.CONFLICT;
        return new Result(outcome, raw);
    }
}
