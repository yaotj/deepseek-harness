package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.SignStatus;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SignStatusTransitionTest {

    @Test
    void casHitIsDoneAndNeverLoadsCurrentStatus() {
        AtomicInteger loaderCalls = new AtomicInteger();

        SignStatusTransition.Result result = SignStatusTransition.classify(1, SignStatus.UNSIGNED,
                () -> {
                    loaderCalls.incrementAndGet();
                    return "SIGNED";
                });

        assertEquals(SignStatusTransition.Outcome.DONE, result.outcome());
        assertFalse(result.isConflict());
        assertEquals(0, loaderCalls.get(), "CAS 命中时 NEVER 回查");
        assertNull(result.observedStatus(), "DONE 没做过回查，observedStatus MUST 为 null");
    }

    @Test
    void zeroRowsWithTargetAlreadyReachedIsIdempotent() {
        SignStatusTransition.Result result =
                SignStatusTransition.classify(0, SignStatus.UNSIGNED, () -> "UNSIGNED");

        assertEquals(SignStatusTransition.Outcome.IDEMPOTENT, result.outcome());
        assertFalse(result.isConflict());
        assertEquals("UNSIGNED", result.observedStatus());
    }

    @Test
    void zeroRowsWithDifferentStatusIsConflict() {
        SignStatusTransition.Result result =
                SignStatusTransition.classify(0, SignStatus.SIGNED, () -> "UNSIGNED");

        assertEquals(SignStatusTransition.Outcome.CONFLICT, result.outcome());
        assertTrue(result.isConflict());
        assertEquals("UNSIGNED", result.observedStatus());
    }

    @Test
    void unparsableCurrentStatusIsConflictAndKeepsRawValue() {
        assertEquals(SignStatusTransition.Outcome.CONFLICT,
                SignStatusTransition.classify(0, SignStatus.UNSIGNED, () -> null).outcome());
        assertEquals(SignStatusTransition.Outcome.CONFLICT,
                SignStatusTransition.classify(0, SignStatus.UNSIGNED, () -> "unsigned").outcome());

        SignStatusTransition.Result dirty =
                SignStatusTransition.classify(0, SignStatus.UNSIGNED, () -> "SIGNING");
        assertEquals(SignStatusTransition.Outcome.CONFLICT, dirty.outcome());
        assertEquals("SIGNING", dirty.observedStatus());
    }

    @Test
    void nullTargetIsConflict() {
        assertEquals(SignStatusTransition.Outcome.CONFLICT,
                SignStatusTransition.classify(0, null, () -> "SIGNED").outcome());
    }
}
