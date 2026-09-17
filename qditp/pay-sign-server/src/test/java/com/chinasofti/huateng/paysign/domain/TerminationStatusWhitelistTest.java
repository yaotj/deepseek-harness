package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.TerminationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 护栏：解约状态白名单与 6 条 CAS 对齐，FAILED 只能复活成 PENDING、NEVER 直接转 SUCCESS。 */
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

    /** 本轮的核心裁决（用户 2026-09-12）：{@code FAILED} 只能复活成 {@code PENDING}。 */
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

    /** {@code isNotifiable} MUST 与 {@code selectCompensableNotify} 的。 */
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
