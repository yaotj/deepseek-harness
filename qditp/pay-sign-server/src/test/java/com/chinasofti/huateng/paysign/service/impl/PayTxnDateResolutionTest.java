package com.chinasofti.huateng.paysign.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.chinasofti.huateng.paysign.support.PaySignValues;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

/**
 * 钉住 {@code PAY_TXN_DETAIL.TXN_DATE} 的取值口径：**优先用发起方透传的行程日，NEVER 默认本地当日**。
 *
 * <p>为什么值得单独建网：{@code GATE_TXN_PAY} 与 {@code PAY_TXN_DETAIL} 按
 * {@code (ORDER_NO, TXN_DATE)} 一一对应，且两张表都以该列做月分区。一旦支付侧取「本地当日」，
 * 跨零点的那批订单就会落在与行程侧不同的日期上 —— 两表再也 join 不上、对账取不到、
 * 补偿按 {@code (ORDER_NO, TXN_DATE)} 也找不回来。已实测出站到落库的滞后可达 94 分钟，
 * 22:26 之后出站的行程随时能踩到。
 *
 * <p>这个缺陷**编译、启动、单笔手工验证全都发现不了**：白天跑一整天都对，只有跨零点那几分钟错，
 * 且错了之后没有任何报错，只是 join 少了行。因此断言 MUST 用「明显不是今天」的日期
 * （见 {@link #passedThroughDateIsKeptEvenWhenItIsNotToday}），NEVER 拿 {@code LocalDate.now()}
 * 当输入 —— 那样把 bug 写进期望值里，网就是空的。
 *
 * <p>无参的 {@code resolveTxnDate()} 一并钉住：{@code PAY_REFUND_DETAIL} 与
 * {@code PAY_CALLBACK_LOG} 是各自独立的事件、日期就该是它们自己发生的日期，
 * **NEVER 把带参重载套到那两处**，也 NEVER 反过来把无参那版改成读请求。
 */
class PayTxnDateResolutionTest {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 透传值原样采用。取一个绝不可能是「今天」的日期，避免回落写法也能蒙对。 */
    @Test
    void passedThroughDateIsUsed() {
        assertEquals("20250101", resolve("20250101"));
    }

    /**
     * 核心不变量：透传的行程日**与本地当日不同时，仍然原样返回**。
     *
     * <p>用 2025-01-01 这种绝不可能是「今天」的值，因此本用例只有在真的读了入参时才通过；
     * 任何「回落到 now()」的写法都会在这里失败。
     */
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

    /**
     * 2026-09-15 起两个重载都在 {@link PaySignValues}（原先是 {@code PaySignWorkflow} 的私有方法，
     * 本用例靠反射调用）。**NEVER 退回反射**：反射版在方法搬家后抛的是「反射调用失败」，
     * 看起来像测试坏了、而不是行为变了，真正的回归会被这条噪音盖住。
     */
    private String resolve(String requestTxnDate) {
        return PaySignValues.resolveTxnDate(requestTxnDate);
    }
}
