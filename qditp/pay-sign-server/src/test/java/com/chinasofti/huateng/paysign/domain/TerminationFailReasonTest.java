package com.chinasofti.huateng.paysign.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminationFailReasonTest {

    private static final String ORIGINAL = "扫描超时未收到支付平台回调";

    @Test
    void roundTripRestoresOriginalReasonExactly() {
        String stored = TerminationFailReason.successConflictNote() + ORIGINAL;
        assertEquals(ORIGINAL, TerminationFailReason.stripManualMark(stored),
                "剥离结果 MUST 等于原始失败原因；不等就意味着 APP 收到的文案被改过");

        String storedFailure = TerminationFailReason.failureConflictNote() + ORIGINAL;
        assertEquals(ORIGINAL, TerminationFailReason.stripManualMark(storedFailure),
                "两种矛盾的写格式 MUST 共用同一套剥离逻辑");
    }

    @Test
    void strippedValueNeverLeaksInternalNote() {
        for (String note : new String[]{
                TerminationFailReason.successConflictNote(),
                TerminationFailReason.failureConflictNote()}) {
            String stripped = TerminationFailReason.stripManualMark(note + ORIGINAL);
            assertFalse(stripped.contains(TerminationFailReason.MANUAL_REVIEW_MARK),
                    "剥离后 NEVER 残留人工核对标记，实际=" + stripped);
            assertFalse(stripped.contains("MUST"),
                    "剥离后 NEVER 残留面向运维的内部说明，实际=" + stripped);
        }
    }

    @Test
    void unmarkedReasonPassesThroughUntouched() {
        assertEquals(ORIGINAL, TerminationFailReason.stripManualMark(ORIGINAL));
        assertEquals("", TerminationFailReason.stripManualMark(""));
        assertNull(TerminationFailReason.stripManualMark(null),
                "null MUST 原样返回，交由下游回落默认文案");
    }

    @Test
    void markedButSeparatorMissingYieldsEmptyRatherThanLeak() {
        String broken = TerminationFailReason.MANUAL_REVIEW_MARK + "被手工截断的说明";
        assertEquals("", TerminationFailReason.stripManualMark(broken),
                "分隔符缺失时 MUST 返回空串，NEVER 返回带内部说明的原值");
    }

    @Test
    void markLiteralAndNotePrefixArePinned() {
        assertEquals("[需人工核对:解约结果矛盾]", TerminationFailReason.MANUAL_REVIEW_MARK);
        assertTrue(TerminationFailReason.successConflictNote()
                        .startsWith(TerminationFailReason.MANUAL_REVIEW_MARK),
                "写入格式 MUST 以标记开头，否则 INSTR 幂等闸门与运维检索都失效");
        assertTrue(TerminationFailReason.failureConflictNote()
                        .startsWith(TerminationFailReason.MANUAL_REVIEW_MARK),
                "写入格式 MUST 以标记开头，否则 INSTR 幂等闸门与运维检索都失效");
        assertFalse(TerminationFailReason.successConflictNote()
                        .equals(TerminationFailReason.failureConflictNote()),
                "两种矛盾的说明 MUST 可区分，否则运维无法判断该按哪种口径处置");
    }
}
