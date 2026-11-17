package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.TerminationStatus;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerminationStatusTransitionTest {

    @Test
    void doneNeverLoadsCurrentStatus() {
        AtomicInteger loaderCalls = new AtomicInteger();

        TerminationStatusTransition.Result result = TerminationStatusTransition.classify(
                1, TerminationStatus.SUCCESS, () -> {
                    loaderCalls.incrementAndGet();
                    return TerminationStatus.SUCCESS.name();
                });

        assertEquals(TerminationStatusTransition.Outcome.DONE, result.outcome());
        assertTrue(result.isDone());
        assertFalse(result.isConflict());
        assertNull(result.observedStatus(), "DONE 时不做回查，observedStatus MUST 为 null");
        assertEquals(0, loaderCalls.get(), "CAS 命中时 NEVER 回查");
    }

    @Test
    void zeroRowsWithTargetStatusIsIdempotent() {
        TerminationStatusTransition.Result result = TerminationStatusTransition.classify(
                0, TerminationStatus.SUCCESS, () -> "SUCCESS");

        assertEquals(TerminationStatusTransition.Outcome.IDEMPOTENT, result.outcome());
        assertFalse(result.isConflict());
        assertEquals("SUCCESS", result.observedStatus());
    }

    @Test
    void zeroRowsWithOtherStatusIsConflict() {
        TerminationStatusTransition.Result result = TerminationStatusTransition.classify(
                0, TerminationStatus.SUCCESS, () -> "FAILED");

        assertEquals(TerminationStatusTransition.Outcome.CONFLICT, result.outcome());
        assertTrue(result.isConflict());
        assertEquals("FAILED", result.observedStatus(), "冲突时 MUST 把库内真实状态带出来供日志与人工核对");
    }

    @Test
    void unparsableStatusIsConflictNotIdempotent() {
        assertEquals(TerminationStatusTransition.Outcome.CONFLICT,
                TerminationStatusTransition.classify(0, TerminationStatus.SUCCESS, () -> null).outcome());
        assertEquals(TerminationStatusTransition.Outcome.CONFLICT,
                TerminationStatusTransition.classify(0, TerminationStatus.SUCCESS, () -> "  ").outcome());
        assertEquals(TerminationStatusTransition.Outcome.CONFLICT,
                TerminationStatusTransition.classify(0, TerminationStatus.SUCCESS, () -> "success").outcome(),
                "大小写不符属脏值，MUST 判冲突而不是宽松匹配");
        assertEquals(TerminationStatusTransition.Outcome.CONFLICT,
                TerminationStatusTransition.classify(0, TerminationStatus.SUCCESS, () -> "DONE").outcome());
    }

    @Test
    void nullTargetIsAlwaysConflict() {
        TerminationStatusTransition.Result result = TerminationStatusTransition.classify(
                0, null, () -> "SUCCESS");

        assertEquals(TerminationStatusTransition.Outcome.CONFLICT, result.outcome());
    }
}
