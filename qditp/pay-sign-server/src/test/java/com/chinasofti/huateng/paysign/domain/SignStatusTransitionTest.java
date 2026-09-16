package com.chinasofti.huateng.paysign.domain;

import com.chinasofti.huateng.model.domain.SignStatus;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SignStatusTransition} 的回归测试（ADR-D40）。
 *
 * <p>此前 {@code ContractDomainServiceImpl.removeSignAgreement} 与
 * {@code PaySignWorkflow.applyGatewayStatus} 各写一遍这段判定、且一个用正向比较一个用取反，
 * 现在收口成一处。<b>本类是那条规则的唯一断言点，改判定 MUST 同步改这里。</b></p>
 */
class SignStatusTransitionTest {

    /** CAS 命中就是 DONE，且 <b>MUST 不回查</b>——回查一次是一次多余的 DB 往返。 */
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

    /**
     * 返 0 行且库里不是目标态 = 真冲突。这一条对应「已解约通道被迟到的签约回调覆盖」那个必须挡住的场景：
     * 目标 SIGNED 而库里是 UNSIGNED，CAS 的 WHERE 命中 0 行，此处判 CONFLICT。
     */
    @Test
    void zeroRowsWithDifferentStatusIsConflict() {
        SignStatusTransition.Result result =
                SignStatusTransition.classify(0, SignStatus.SIGNED, () -> "UNSIGNED");

        assertEquals(SignStatusTransition.Outcome.CONFLICT, result.outcome());
        assertTrue(result.isConflict());
        assertEquals("UNSIGNED", result.observedStatus());
    }

    /**
     * 库里是 NULL / 脏值 / 大小写不符时一律 CONFLICT，<b>NEVER 兜底成目标态</b>：
     * 猜错方向会把「状态未知」当成「已经成功」，把不一致藏起来。
     * {@code observedStatus} 仍返回原始值，供日志与错误消息如实呈现。
     */
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
