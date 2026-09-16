package com.chinasofti.huateng.facepay.domain;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLIntegrityConstraintViolationException;

/**
 * 「唯一索引冲突 = 幂等命中」这条判定的唯一实现。
 *
 * <p>本模块的幂等主写法是「唯一索引 + 撞键后回查」，而 {@code catch (DuplicateKeyException)}
 * <b>只认最外层异常类型</b>。一旦本模块打开 {@code management.tracing.enabled}，
 * {@code resource/micro/web} 的观测切面会把异常<b>重新包一层</b>，
 * 12 处兜底会同时静默失效（AGENTS.md §5.2）。因此判定 MUST 沿 {@code getCause()} 链走。
 *
 * <p><b>为什么这里破例建了一个类</b>：同型判定在 {@code card-pool-server} /
 * {@code ticket-server} / {@code gate-txn-pay-server} / {@code daily-ticket-server} /
 * {@code collect-pay-server} 都是「每个类抄一份 private 方法」，因为那些模块各只有 1~3 处。
 * 本模块有 <b>12 处、分散在 9 个类</b>，照抄就是 9 份逐字相同的私有方法 ——
 * 与本次重构「消除重复」的目标正相反。本类<b>只有这一个方法、只表达这一条领域规则</b>，
 * NEVER 往里塞第二个用途（那才是 AGENTS.md 禁止的 {@code XxxUtils} 杂物袋）。
 *
 * <p>三种类型都要认：Spring 的 {@link DuplicateKeyException} 与
 * {@link DataIntegrityViolationException}，以及被剥到底层时的
 * {@link SQLIntegrityConstraintViolationException}。</p>
 */
public final class F2fDuplicateKey {

    private F2fDuplicateKey() {
    }

    /**
     * 沿 cause 链判断是否唯一索引 / 完整性约束冲突。
     *
     * <p>自环 cause（{@code cur.getCause() == cur}）要单独挡一下，否则死循环。</p>
     *
     * @param throwable 捕到的异常，允许为 null
     * @return true 表示是撞键，调用方可走幂等回查；false 表示<b>与幂等无关</b>，MUST 原样上抛
     */
    public static boolean isConflict(Throwable throwable) {
        for (Throwable cursor = throwable; cursor != null; ) {
            if (cursor instanceof DuplicateKeyException
                    || cursor instanceof DataIntegrityViolationException
                    || cursor instanceof SQLIntegrityConstraintViolationException) {
                return true;
            }
            cursor = cursor.getCause() == cursor ? null : cursor.getCause();
        }
        return false;
    }
}
