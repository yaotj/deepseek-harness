package org.qd.scg.dual;

import java.util.concurrent.atomic.AtomicInteger;


public class SideCircuitBreaker {

    private final int failureThreshold;
    private final long openMillis;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long openUntilEpochMs = 0;

    public SideCircuitBreaker(int failureThreshold, long openMillis) {
        this.failureThreshold = failureThreshold;
        this.openMillis = openMillis;
    }

    public boolean allowRequest() {
        return System.currentTimeMillis() >= openUntilEpochMs;
    }

    public boolean isOpen() {
        return !allowRequest();
    }

    public void onSuccess() {
        consecutiveFailures.set(0);
        openUntilEpochMs = 0;
    }

    public void onFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openUntilEpochMs = System.currentTimeMillis() + openMillis;
        }
    }

    int consecutiveFailures() {
        return consecutiveFailures.get();
    }
}
