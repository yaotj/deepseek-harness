package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.SignStatus;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 护栏：CAS 命中不回查；0 行按库内状态区分幂等与冲突，未知状态一律 CONFLICT、NEVER 兜底成目标态。 */
class SignStatusTransitionTest {

    /** CAS 命中就是 DONE，且 MUST 不回查——回查一次是一次多余的 DB 往返。 */
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

    /** 返 0 行但库里已是目标态 = 上游重放，两个调用点都按成功处理。 */
    @Test
    void zeroRowsWithTargetAlreadyReachedIsIdempotent() {
        SignStatusTransition.Result result =
                SignStatusTransition.classify(0, SignStatus.UNSIGNED, () -> "UNSIGNED");

        assertEquals(SignStatusTransition.Outcome.IDEMPOTENT, result.outcome());
        assertFalse(result.isConflict());
        assertEquals("UNSIGNED", result.observedStatus());
    }

    /** 返 0 行且库里不是目标态 = 真冲突。这一条对应「已解约通道被迟到的签约回调覆盖」那个必须挡住的场景。 */
    @Test
    void zeroRowsWithDifferentStatusIsConflict() {
        SignStatusTransition.Result result =
                SignStatusTransition.classify(0, SignStatus.SIGNED, () -> "UNSIGNED");

        assertEquals(SignStatusTransition.Outcome.CONFLICT, result.outcome());
        assertTrue(result.isConflict());
        assertEquals("UNSIGNED", result.observedStatus());
    }

    /** 库里是 NULL / 脏值 / 大小写不符时一律 CONFLICT，NEVER 兜底成目标态。 */
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

    /** 目标态本身解析不出来（支付平台返了未知状态）时也是 CONFLICT，NEVER 当成一致。 */
    @Test
    void nullTargetIsConflict() {
        assertEquals(SignStatusTransition.Outcome.CONFLICT,
                SignStatusTransition.classify(0, null, () -> "SIGNED").outcome());
    }
}
