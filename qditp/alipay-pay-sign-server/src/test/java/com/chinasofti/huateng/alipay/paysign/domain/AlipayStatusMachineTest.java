package com.chinasofti.huateng.alipay.paysign.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinasofti.huateng.model.domain.AlipaySignStatus;
import com.chinasofti.huateng.model.domain.AlipayTerminationStatus;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;


class AlipayStatusMachineTest {

    @Test
    void signStatusOnlyAllowsSignedToTerminated() {
        assertTrue(AlipaySignStatus.SIGNED.canTransitTo(AlipaySignStatus.TERMINATED));
        assertFalse(AlipaySignStatus.TERMINATED.canTransitTo(AlipaySignStatus.SIGNED),
                "NEVER 允许 TERMINATED -> SIGNED：已销卡的协议被迟到的签约登记覆盖，"
                        + "扣款链路会拿一个渠道侧已注销的协议号继续扣费");
        assertTrue(AlipaySignStatus.TERMINATED.isTerminal());
        assertFalse(AlipaySignStatus.SIGNED.isTerminal());
    }

    @Test
    void terminationStatusHasTwoTerminalStatesAndNeitherRevives() {
        assertTrue(AlipayTerminationStatus.PENDING.canTransitTo(AlipayTerminationStatus.COMPLETED));
        assertTrue(AlipayTerminationStatus.PENDING.canTransitTo(AlipayTerminationStatus.FAIL));
        assertFalse(AlipayTerminationStatus.FAIL.canTransitTo(AlipayTerminationStatus.PENDING),
                "FAIL 是永久终态（签约信息不存在这类），重试多少次也不会自愈");
        assertFalse(AlipayTerminationStatus.COMPLETED.canTransitTo(AlipayTerminationStatus.PENDING));
        assertTrue(AlipayTerminationStatus.PENDING.isActionable());
        assertFalse(AlipayTerminationStatus.COMPLETED.isActionable());
    }

    @Test
    void parseOrNullSwallowsDirtyValuesInsteadOfThrowing() {
        assertNull(AlipaySignStatus.parseOrNull(null));
        assertNull(AlipaySignStatus.parseOrNull("  "));
        assertNull(AlipaySignStatus.parseOrNull("signed"), "大小写不符也算脏值：库里存的一律是大写");
        assertEquals(AlipaySignStatus.SIGNED, AlipaySignStatus.parseOrNull("SIGNED"));
        assertNull(AlipayTerminationStatus.parseOrNull("FAILED"),
                "FAILED 是 pay-sign 侧的词表，本渠道是 FAIL，NEVER 互认");
    }

    @Test
    void classifyNeverLoadsCurrentStatusWhenCasHits() {
        AtomicInteger loadCount = new AtomicInteger();

        AlipaySignStatusTransition.Result result = AlipaySignStatusTransition.classify(
                1, AlipaySignStatus.TERMINATED, () -> {
                    loadCount.incrementAndGet();
                    return "TERMINATED";
                });

        assertEquals(AlipaySignStatusTransition.Outcome.DONE, result.outcome());
        assertNull(result.observedStatus());
        assertEquals(0, loadCount.get());
    }

    @Test
    void classifyTellsIdempotentApartFromConflict() {
        AlipaySignStatusTransition.Result idempotent = AlipaySignStatusTransition.classify(
                0, AlipaySignStatus.TERMINATED, () -> "TERMINATED");
        assertEquals(AlipaySignStatusTransition.Outcome.IDEMPOTENT, idempotent.outcome());
        assertTrue(idempotent.reachedTarget());

        AlipaySignStatusTransition.Result conflict = AlipaySignStatusTransition.classify(
                0, AlipaySignStatus.TERMINATED, () -> null);
        assertTrue(conflict.isConflict(), "库里既不是 SIGNED 也不是 TERMINATED 时 MUST 报 CONFLICT 交人工");
        assertFalse(conflict.reachedTarget());
        assertNull(conflict.observedStatus());
    }
}
