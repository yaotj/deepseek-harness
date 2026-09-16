package com.chinasofti.huateng.facepay.domain;

import java.util.function.Supplier;

/**
 * {@code F2F_ORDER.ORDER_STATUS} 的 CAS 结果解读器。三件套（{@code docs/domain/state-machines.md} §二）
 * 的第 ③ 件，形态<b>照抄</b> {@code pay-sign-server/.../domain/SignStatusTransition}（规范点名的参考实现）。
 *
 * <p>它只做一件事：把 {@code updateStatus} / {@code markPaid} 返回的影响行数，翻译成
 * {@code DONE} / {@code IDEMPOTENT} / {@code CONFLICT} 三态 + 回查到的原始状态。
 * 在此之前 9 个服务各自写 {@code if (updated == 0) log.warn(...)}，
 * <b>「已经是目标态」与「状态被别人推走了」两种情况混在一个分支里</b> ——
 * 前者是正常的重复上报，后者是真冲突，需要不同处置。</p>
 *
 * <p><b>三条 NEVER</b>（前两条与 ADR-D40 的裁决一致，第三条是本项目特有）：</p>
 * <ol>
 *   <li><b>NEVER 在这里加 CAS 前的白名单校验</b>（{@link F2fOrderStatus#canTransitTo}）。
 *       权威白名单是三条 CAS 自己的 WHERE；调用方手里的「当前状态」来自更早一次 select、
 *       随时可能过期，用过期值提前拦一道只会<b>误拦</b>，且比 CAS 返 0 行更难排查。</li>
 *   <li><b>NEVER 扩成通用状态机引擎。</b> 三条 CAS 各带不同副作用列
 *       （{@code FULFILL_TMS} / {@code PAID_TMS} / 激活四列），归一成一条
 *       {@code transit(key, from, to)} 会把这些列丢掉 —— 规范里那段伪代码正是这么写的，已否决。</li>
 *   <li><b>NEVER 让 {@code CONFLICT} 一律变成对上游报错。</b> 本模块的调用方是 TVM / BOM / APP
 *       三种设备，各自的 retCode 族与容错口径都不同（设备侧拿不到「稍后重试」这种语义）。
 *       典型处置是「按库内真实状态继续走完应答 + 打 WARN」，与 pay-sign 的返 409 不同。</li>
 * </ol>
 */
public final class F2fOrderStatusTransition {

    private F2fOrderStatusTransition() {
    }

    /** CAS 迁移的三种结局。<b>调用方 MUST 显式处理 {@link #CONFLICT}，NEVER 静默忽略。</b> */
    public enum Outcome {
        /** CAS 命中，状态已按预期推进。 */
        DONE,
        /** CAS 未命中，但库内已是目标态 —— 重复上报 / 重复回调，按成功处理。 */
        IDEMPOTENT,
        /** CAS 未命中且库内不是目标态 —— 状态被别人推走了，或前置状态本就不满足。 */
        CONFLICT
    }

    /**
     * 判定结果。{@code observedStatus} 只在 CAS 返 0 行时才有值（{@code DONE} 时为 {@code null}），
     * 原样保留库里的字符串，<b>不做归一</b> —— 脏值本身就是排查线索。
     */
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
     * <p><b>{@code currentStatusLoader} 只在 {@code updatedRows == 0} 时被调用</b>，
     * 「CAS 命中就不回查」是规则的一部分：改成先无条件回查等于给每次成功迁移加一次多余 DB 往返。</p>
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
        // 解析不出来（NULL / 脏值 / 大小写不符）一律按 CONFLICT，NEVER 兜底成目标态：
        // 把「不认识的状态」当成「已经到位」会把真冲突伪装成幂等，是最难查的一类。
        F2fOrderStatus current = F2fOrderStatus.parseOrNull(raw);
        Outcome outcome = target != null && target == current ? Outcome.IDEMPOTENT : Outcome.CONFLICT;
        return new Result(outcome, raw);
    }
}
