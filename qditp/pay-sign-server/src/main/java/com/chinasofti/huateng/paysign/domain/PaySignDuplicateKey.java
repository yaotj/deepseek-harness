package com.chinasofti.huateng.paysign.domain;

import org.springframework.dao.DuplicateKeyException;

/**
 * 唯一键冲突的**cause 链**判定（2026-09-17，ADR-D123）。
 *
 * <p>本模块打开了 {@code management.tracing.enabled}，{@code resource/micro/web} 的观测切面会把
 * 异常重新包一层，因此 <b>NEVER 写裸 {@code catch (DuplicateKeyException)}</b> —— 那样的兜底在本模块
 * 静默落空、退化成对上游报错（ADR-D53）。统一模板：
 * <pre>
 * catch (RuntimeException e) {
 *     if (!PaySignDuplicateKey.isConflict(e)) { throw e; }
 *     ... 回查后按幂等处理 ...
 * }
 * </pre>
 *
 * <p>这是对 AGENTS.md §5.1「NEVER 新建工具类」的**有意破例**，与 {@code face-pay-server} 的
 * {@code F2fDuplicateKey} 同形同因：本模块有 3 处调用点（签约结果回调、免密扣款建单、解约申请补建），
 * 抄私有方法等于 3 份逐字副本。
 */
public final class PaySignDuplicateKey {

    private PaySignDuplicateKey() {
    }

    /**
     * 异常链上是否出现过唯一键冲突。
     *
     * @param e 捕获到的异常，允许为 {@code null}
     * @return 链上任一层是 {@link DuplicateKeyException} 即 true
     */
    public static boolean isConflict(Throwable e) {
        for (Throwable cause = e; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }
}
