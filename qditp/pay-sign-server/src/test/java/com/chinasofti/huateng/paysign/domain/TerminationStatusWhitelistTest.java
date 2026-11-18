package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.TerminationStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminationStatusWhitelistTest {

    @Test
    void pendingAllowsScanningAndFailedOnly() {
        assertTrue(TerminationStatus.PENDING.canTransitTo(TerminationStatus.SCANNING));
        assertTrue(TerminationStatus.PENDING.canTransitTo(TerminationStatus.FAILED));
        assertFalse(TerminationStatus.PENDING.canTransitTo(TerminationStatus.SUCCESS),
                "PENDING 尚未做未结清欠费校验，直接判成功等于绕过前置校验——但 receiveTerminationResult 是外部回调，已确认解约结果，允许 PENDING->SUCCESS");
        assertFalse(TerminationStatus.PENDING.canTransitTo(TerminationStatus.PENDING));
    }

    @Test
    void scanningAllowsSuccessFailedAndRevert() {
        assertTrue(TerminationStatus.SCANNING.canTransitTo(TerminationStatus.SUCCESS));
        assertTrue(TerminationStatus.SCANNING.canTransitTo(TerminationStatus.FAILED));
        assertTrue(TerminationStatus.SCANNING.canTransitTo(TerminationStatus.PENDING),
                "revertScanningToPending 是在跑的语句，白名单 MUST 容纳它");
    }

    @Test
    void failedNeverTransitsToSuccess() {
        assertTrue(TerminationStatus.FAILED.canTransitTo(TerminationStatus.PENDING));
        assertFalse(TerminationStatus.FAILED.canTransitTo(TerminationStatus.SUCCESS),
                "NEVER 允许 FAILED -> SUCCESS：APP 已收到解约失败通知，改判成功需业务先定补通知口径");
        assertFalse(TerminationStatus.FAILED.canTransitTo(TerminationStatus.SCANNING),
                "重新申请 MUST 经 PENDING 走完整扫表流程，NEVER 跳过欠费校验直接进 SCANNING");
    }

    @Test
    void successIsTheOnlyTerminalState() {
        assertTrue(TerminationStatus.SUCCESS.isTerminal());
        assertFalse(TerminationStatus.PENDING.isTerminal());
        assertFalse(TerminationStatus.SCANNING.isTerminal());
        assertFalse(TerminationStatus.FAILED.isTerminal(), "FAILED 可复活成 PENDING，不是终态");
    }

    @Test
    void onlyTerminalStatusesAreNotifiable() {
        assertTrue(TerminationStatus.SUCCESS.isNotifiable());
        assertTrue(TerminationStatus.FAILED.isNotifiable());
        assertFalse(TerminationStatus.PENDING.isNotifiable());
        assertFalse(TerminationStatus.SCANNING.isNotifiable());
    }

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
