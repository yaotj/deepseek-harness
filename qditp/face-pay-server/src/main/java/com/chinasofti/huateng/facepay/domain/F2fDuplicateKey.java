package com.chinasofti.huateng.facepay.domain;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLIntegrityConstraintViolationException;

/** 「唯一索引冲突 = 幂等命中」这条判定的唯一实现。这是对 AGENTS.md §5.1「NEVER 新建工具类」的有意破例（12 处判定散在 9 个类）。 */
public final class F2fDuplicateKey {

    private F2fDuplicateKey() {
    }

    /**
     * 沿 cause 链判断是否唯一索引 / 完整性约束冲突。
     *
     * @param throwable 捕到的异常，允许为 null
     * @return true 表示是撞键，调用方可走幂等回查；false 表示与幂等无关，MUST 原样上抛
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
