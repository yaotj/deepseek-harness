package org.qd.scg.dual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SideCircuitBreakerTest {

    @Test
    void opensAfterThresholdAndRecovers() throws Exception {
        SideCircuitBreaker breaker = new SideCircuitBreaker(3, 50);
        assertTrue(breaker.allowRequest());

        breaker.onFailure();
        breaker.onFailure();
        assertTrue(breaker.allowRequest());

        breaker.onFailure();
        assertFalse(breaker.allowRequest());
        assertTrue(breaker.isOpen());

        Thread.sleep(60);
        assertTrue(breaker.allowRequest());

        breaker.onSuccess();
        assertTrue(breaker.allowRequest());
        assertFalse(breaker.isOpen());
    }

    @Test
    void successResetsConsecutiveFailures() {
        SideCircuitBreaker breaker = new SideCircuitBreaker(2, 10_000);
        breaker.onFailure();
        breaker.onSuccess();
        breaker.onFailure();
        assertTrue(breaker.allowRequest());
    }
}
