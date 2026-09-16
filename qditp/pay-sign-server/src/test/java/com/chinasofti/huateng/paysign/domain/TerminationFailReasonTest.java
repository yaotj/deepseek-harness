package com.chinasofti.huateng.paysign.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁死 {@code FAIL_REASON} 的写格式与读格式必须互逆。
 *
 * <p>存在理由是一个已发生的缺陷（2026-09-12，ADR-D47 引入、同日发现）：写入方把「需人工核对」的
 * 内部说明前置拼进 {@code FAIL_REASON}，而 {@code AppNotifyServiceImpl.asyncRetryTerminationNotify}
 * 的补偿重发会把该列原值当成 {@code terminationResultMsg} 发给终端用户。**这组用例是那条链路唯一的护栏**
 * —— 该模块没有能装配 {@code AppNotifyServiceImpl} 的 mock 脚手架。
 */
class TerminationFailReasonTest {

    private static final String ORIGINAL = "扫描超时未收到支付平台回调";

    /** 写进去再剥出来 MUST 得到一模一样的原始原因，一个字符都不能差。 */
    @Test
    void roundTripRestoresOriginalReasonExactly() {
        String stored = TerminationFailReason.successConflictNote() + ORIGINAL;
        assertEquals(ORIGINAL, TerminationFailReason.stripManualMark(stored),
                "剥离结果 MUST 等于原始失败原因；不等就意味着 APP 收到的文案被改过");

        String storedFailure = TerminationFailReason.failureConflictNote() + ORIGINAL;
        assertEquals(ORIGINAL, TerminationFailReason.stripManualMark(storedFailure),
                "两种矛盾的写格式 MUST 共用同一套剥离逻辑");
    }

    /**
     * 剥离结果里 **NEVER** 残留标记 —— 这条直接对应那次缺陷的表现。
     *
     * <p>用 contains 而不是 startsWith：标记若因为将来改成后置拼接而出现在中间，本条同样要红。
     */
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

    /** 没有标记的普通失败原因 MUST 原样返回，NEVER 被误伤。 */
    @Test
    void unmarkedReasonPassesThroughUntouched() {
        assertEquals(ORIGINAL, TerminationFailReason.stripManualMark(ORIGINAL));
        assertEquals("", TerminationFailReason.stripManualMark(""));
        assertNull(TerminationFailReason.stripManualMark(null),
                "null MUST 原样返回，交由下游回落默认文案");
    }

    /**
     * 带标记但分隔符缺失（历史脏数据或手工改库）时 MUST 返回空串。
     *
     * <p>宁可让下游回落到「存在扣费失败订单」这种默认文案，也 NEVER 把内部说明整段发出去。
     */
    @Test
    void markedButSeparatorMissingYieldsEmptyRatherThanLeak() {
        String broken = TerminationFailReason.MANUAL_REVIEW_MARK + "被手工截断的说明";
        assertEquals("", TerminationFailReason.stripManualMark(broken),
                "分隔符缺失时 MUST 返回空串，NEVER 返回带内部说明的原值");
    }

    /**
     * 标记字面量与「两种说明都以它开头」一并钉住。
     *
     * <p>该字面量同时是 mapper 里 {@code INSTR} 幂等判据的入参与运维检索关键字，
     * 改动 MUST 同步 mapper Javadoc 与 ADR-D47。
     */
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
