package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.chinasofti.huateng.paysign.support.PaySignValues;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

/** 护栏：TXN_DATE 优先取透传行程日、NEVER 回落本地当日（跨零点会让两张分区表 join 不上）。 */
class PayTxnDateResolutionTest {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 透传值原样采用。取一个绝不可能是「今天」的日期，避免回落写法也能蒙对。 */
    @Test
    void passedThroughDateIsUsed() {
        assertEquals("20250101", resolve("20250101"));
    }

    /** 核心不变量：透传的行程日**与本地当日不同时，仍然原样返回**。 */
    @Test
    void passedThroughDateIsKeptEvenWhenItIsNotToday() {
        String travelDate = "20250101";
        assertEquals(travelDate, resolve(travelDate),
                "行程日 MUST 原样透传，NEVER 用本地当日覆盖 —— 跨零点时两表就是靠这一列对齐的");
    }

    /** 前后空白要清掉：透传值直接进 (ORDER_NO, TXN_DATE) 唯一索引，带空格即对不上。 */
    @Test
    void passedThroughDateIsTrimmed() {
        assertEquals("20250101", resolve("  20250101  "));
    }

    /** 上游没带（老镜像）才回落本地当日，属降级而非正常路径。 */
    @Test
    void missingDateFallsBackToToday() {
        assertEquals(today(), resolve(null));
    }

    /** 空串与纯空白同样视为没带，NEVER 当成合法日期写进分区列。 */
    @Test
    void blankDateFallsBackToToday() {
        assertEquals(today(), resolve(""));
        assertEquals(today(), resolve("   "));
    }

    /** 无参版仍是本地当日：退款与回调日志用的是它，口径 NEVER 与上面那条混用。 */
    @Test
    void noArgVariantStillResolvesToToday() {
        assertEquals(today(), PaySignValues.resolveTxnDate());
    }

    private String today() {
        return LocalDate.now().format(YYYYMMDD);
    }

    /** 2026-09-15 起两个重载都在 {@link PaySignValues}（原先是 {@code PaySignWorkflow} 的私有方法。 */
    private String resolve(String requestTxnDate) {
        return PaySignValues.resolveTxnDate(requestTxnDate);
    }
}
