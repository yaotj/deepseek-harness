package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.TerminationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TerminationStatus} 白名单与解析的行为锁。
 *
 * <p>白名单与 {@code AppTerminationRequestMapper.xml} 的 6 条 CAS 一一对应，
 * 改动任何一边都 MUST 同步另一边，本测试就是那个提醒。
 */
class TerminationStatusWhitelistTest {

    /** PENDING 只能进 SCANNING（抢占）或 FAILED（有未结清欠费被拒）。 */
    @Test
    void pendingAllowsScanningAndFailedOnly() {
        assertTrue(TerminationStatus.PENDING.canTransitTo(TerminationStatus.SCANNING));
        assertTrue(TerminationStatus.PENDING.canTransitTo(TerminationStatus.FAILED));
        assertFalse(TerminationStatus.PENDING.canTransitTo(TerminationStatus.SUCCESS),
                "PENDING 尚未做未结清欠费校验，直接判成功等于绕过前置校验");
        assertFalse(TerminationStatus.PENDING.canTransitTo(TerminationStatus.PENDING));
    }

    /** SCANNING 三个出口：回调收口 SUCCESS、答复失败或超时 FAILED、明确失败后交还执行权 PENDING。 */
    @Test
    void scanningAllowsSuccessFailedAndRevert() {
        assertTrue(TerminationStatus.SCANNING.canTransitTo(TerminationStatus.SUCCESS));
        assertTrue(TerminationStatus.SCANNING.canTransitTo(TerminationStatus.FAILED));
        assertTrue(TerminationStatus.SCANNING.canTransitTo(TerminationStatus.PENDING),
                "revertScanningToPending 是在跑的语句，白名单 MUST 容纳它");
    }

    /**
     * <b>本轮的核心裁决</b>（用户 2026-09-12）：{@code FAILED} 只能复活成 {@code PENDING}，
     * NEVER 直接转 {@code SUCCESS}。
     *
     * <p>删掉这条断言等于允许「已告知 APP 解约失败之后，迟到的成功回调再改成成功」，
     * 对端会收到两条相反通知，而补通知的口径没有业务定义。
     */
    @Test
    void failedNeverTransitsToSuccess() {
        assertTrue(TerminationStatus.FAILED.canTransitTo(TerminationStatus.PENDING));
        assertFalse(TerminationStatus.FAILED.canTransitTo(TerminationStatus.SUCCESS),
                "NEVER 允许 FAILED -> SUCCESS：APP 已收到解约失败通知，改判成功需业务先定补通知口径");
        assertFalse(TerminationStatus.FAILED.canTransitTo(TerminationStatus.SCANNING),
                "重新申请 MUST 经 PENDING 走完整扫表流程，NEVER 跳过欠费校验直接进 SCANNING");
    }

    /** SUCCESS 是唯一终态：签约记录已删、账户域通道已清，回退只会引来重复投递。 */
    @Test
    void successIsTheOnlyTerminalState() {
        assertTrue(TerminationStatus.SUCCESS.isTerminal());
        assertFalse(TerminationStatus.PENDING.isTerminal());
        assertFalse(TerminationStatus.SCANNING.isTerminal());
        assertFalse(TerminationStatus.FAILED.isTerminal(), "FAILED 可复活成 PENDING，不是终态");
    }

    /**
     * {@code isNotifiable} MUST 与 {@code selectCompensableNotify} 的
     * {@code TERMINATION_STATUS in ('SUCCESS','FAILED')} 保持一致。
     * 放宽到 PENDING / SCANNING 会给 APP 发假解约成功通知（2026-08-26 修过一次）。
     */
    @Test
    void onlyTerminalStatusesAreNotifiable() {
        assertTrue(TerminationStatus.SUCCESS.isNotifiable());
        assertTrue(TerminationStatus.FAILED.isNotifiable());
        assertFalse(TerminationStatus.PENDING.isNotifiable());
        assertFalse(TerminationStatus.SCANNING.isNotifiable());
    }

    /** 宽松解析：认得的原样返回，认不得的一律 null，NEVER 兜底成具体状态。 */
    @Test
    void parseOrNullNeverGuesses() {
        assertSame(TerminationStatus.SCANNING, TerminationStatus.parseOrNull("SCANNING"));
        assertNull(TerminationStatus.parseOrNull(null));
        assertNull(TerminationStatus.parseOrNull(""));
        assertNull(TerminationStatus.parseOrNull(" "));
        assertNull(TerminationStatus.parseOrNull("scanning"), "大小写不符按未知处理");
        assertNull(TerminationStatus.parseOrNull("UNSIGNED"), "UNSIGNED 属签约状态机，NEVER 混进来");
    }
}
