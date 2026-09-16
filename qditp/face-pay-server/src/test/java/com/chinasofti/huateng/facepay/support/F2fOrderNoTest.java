package com.chinasofti.huateng.facepay.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 订单号格式测试。纯函数，不需要 Spring 也不需要数据库。
 *
 * <p>断言写死字面量：长度口径还没跟 ACC 对账侧确认，一旦要回退到 20 位，这些用例就是回退是否
 * 改干净的判据（设计文档 §十九）。</p>
 */
class F2fOrderNoTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 9, 8, 14, 30, 5);

    @Test
    void formatIs20CharsSameAsLegacy() {
        String orderNo = F2fOrderNo.format(F2fOrderNo.BIZ_SINGLE_TICKET, AT, 7L);

        assertEquals("0020260908143005" + "0007", orderNo);
        assertEquals(20, orderNo.length(), "长度 MUST 与旧实现一致，用户 2026-09-10 裁决「按照旧的来」");
    }

    @Test
    void seqSegmentIsAlwaysFourDigits() {
        assertEquals("0001", tail(F2fOrderNo.format("00", AT, 1L)));
        assertEquals("0099", tail(F2fOrderNo.format("00", AT, 99L)));
        assertEquals("9999", tail(F2fOrderNo.format("00", AT, 9999L)));
    }

    @Test
    void seqBeyond9999WrapsInsteadOfGrowingLength() {
        String orderNo = F2fOrderNo.format("00", AT, 10007L);

        assertEquals(20, orderNo.length(), "序列超界时长度不得漂移，旧实现只 leftPad 会吐出 21 位");
        assertEquals("0007", tail(orderNo));
    }

    @Test
    void badBizCodeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> F2fOrderNo.format("0", AT, 1L));
        assertThrows(IllegalArgumentException.class, () -> F2fOrderNo.format("000", AT, 1L));
        assertThrows(IllegalArgumentException.class, () -> F2fOrderNo.format(null, AT, 1L));
    }

    @Test
    void negativeSeqIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> F2fOrderNo.format("00", AT, -1L));
    }

    private static String tail(String orderNo) {
        return orderNo.substring(orderNo.length() - F2fOrderNo.SEQ_WIDTH);
    }
}
