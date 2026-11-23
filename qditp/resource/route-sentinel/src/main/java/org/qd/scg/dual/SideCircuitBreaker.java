package org.qd.scg.dual;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class SideCircuitBreaker {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private static final Logger log = LoggerFactory.getLogger(SideCircuitBreaker.class);

    private final int failureThreshold;
    private final long openMillis;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicInteger halfOpenQuota = new AtomicInteger();
    private final Object stateLock = new Object();
    private volatile long openUntilEpochMs = 0;
    private volatile State state = State.CLOSED;

    public SideCircuitBreaker(int failureThreshold, long openMillis) {
        this.failureThreshold = failureThreshold;
        this.openMillis = openMillis;
    }

    public boolean allowRequest() {
        State current = state;
        if (current == State.OPEN) {
            long now = System.currentTimeMillis();
            if (now < openUntilEpochMs) {
                return false;
            }
            synchronized (stateLock) {
                if (state == State.OPEN && now >= openUntilEpochMs) {
                    state = State.HALF_OPEN;
                    halfOpenQuota.set(1);
                    log.info("熔断到期，进入 HALF_OPEN，仅放行 1 个探测请求");
                }
            }
            current = state;
        }
        if (current == State.HALF_OPEN) {
            return halfOpenQuota.getAndUpdate(q -> q > 0 ? q - 1 : 0) > 0;
        }
        return current == State.CLOSED;
    }

    public boolean isOpen() {
        State current = state;
        return current == State.OPEN || (current == State.HALF_OPEN && halfOpenQuota.get() <= 0);
    }

    public void onSuccess() {
        State previous;
        synchronized (stateLock) {
            previous = state;
            consecutiveFailures.set(0);
            openUntilEpochMs = 0;
            halfOpenQuota.set(0);
            state = State.CLOSED;
        }
        if (previous == State.HALF_OPEN) {
            log.info("HALF_OPEN 探测成功，熔断器恢复 CLOSED，全量放行");
        }
    }

    public void onFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        State previous;
        synchronized (stateLock) {
            previous = state;
            if (previous == State.HALF_OPEN) {
                state = State.OPEN;
                openUntilEpochMs = System.currentTimeMillis() + openMillis;
                consecutiveFailures.set(failureThreshold);
                halfOpenQuota.set(0);
            } else if (previous == State.CLOSED && failures >= failureThreshold) {
                state = State.OPEN;
                openUntilEpochMs = System.currentTimeMillis() + openMillis;
                halfOpenQuota.set(0);
            }
        }
        if (previous == State.HALF_OPEN) {
            log.warn("HALF_OPEN 探测失败，熔断器重新 OPEN，冷却 {} ms", openMillis);
        } else if (previous == State.CLOSED && state == State.OPEN) {
            log.warn("连续失败 {} 次达到阈值 {}，熔断器 OPEN，冷却 {} ms", failures, failureThreshold, openMillis);
        }
    }

    int consecutiveFailures() {
        return consecutiveFailures.get();
    }
}
