package com.chinasofti.huateng.ticket.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 只钉「对外的 0 / 空 / 2 值域」这一条口径，与 trans-query-server 的
 * {@code TransRecordAssemblerTest} 同表同断言。
 *
 * <p>两份 {@code TransRecordAssembler.toAppDebitResult} MUST 逐字一致（跨模块不能复用，
 * 见 AGENTS.md §4.1），因此两边各留一份用例：改了一边没改另一边，两边测试会各自变红。</p>
 */
class TransRecordAssemblerTest {

    @Test
    void 成功映射0在途映射空其余映射2() {
        assertEquals("0", TransRecordAssembler.toAppDebitResult("SUCCESS", null));
        assertEquals("2", TransRecordAssembler.toAppDebitResult("FAIL", null));
        assertNull(TransRecordAssembler.toAppDebitResult("PROCESSING", null),
                "IT-082 阶段三：在途 MUST 返 null（APP 按「空 = 支付中」渲染）");
        assertNull(TransRecordAssembler.toAppDebitResult("RETRY", null));
        assertNull(TransRecordAssembler.toAppDebitResult("INIT", null));
        assertEquals("2", TransRecordAssembler.toAppDebitResult("CLOSED", null),
                "关单是终态失败，NEVER 当支付中");
        assertEquals("2", TransRecordAssembler.toAppDebitResult("UNKNOWN", null),
                "未知值 MUST fail-closed 落 2");
        assertEquals("0", TransRecordAssembler.toAppDebitResult(null, "SUCCESS"));
        assertEquals("0", TransRecordAssembler.toAppDebitResult("  ", "SUCCESS"));
        assertNull(TransRecordAssembler.toAppDebitResult(null, "PROCESSING"));
        assertEquals("2", TransRecordAssembler.toAppDebitResult(null, null));
    }

    @Test
    void 对外扣款结果只产出0空2且永不产出1和3() {
        String[] authoritativeStatuses = {"SUCCESS", "FAIL", "PROCESSING", "RETRY", "INIT", "CLOSED",
                null, "", "  ", "UNKNOWN"};
        for (String status : authoritativeStatuses) {
            String result = TransRecordAssembler.toAppDebitResult(status, null);
            assertTrue(result == null || "0".equals(result) || "2".equals(result),
                    () -> "status=" + status + " 时只允许 0 / 空 / 2，实得 " + result);
        }
    }
}
