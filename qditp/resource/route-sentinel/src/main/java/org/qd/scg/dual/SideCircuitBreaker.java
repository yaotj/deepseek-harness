package org.qd.scg.dual;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单侧（新 ITP / 老 ITP）熔断器：连续失败 N 次打开，到期后半开试探。
 *
 * <p>线程安全：双发每条支路在独立线程完成，计数与状态均为原子/易变语义。
 */
public class SideCircuitBreaker {

    private final int failureThreshold;
    private final long openMillis;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile long openUntilEpochMs = 0;

    public SideCircuitBreaker(int failureThreshold, long openMillis) {
        this.failureThreshold = failureThreshold;
        this.openMillis = openMillis;
    }

    /** 是否放行本次请求。熔断打开期间直接拒绝（跳过该侧）。 */
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

    /** 测试用：当前连续失败次数。 */
    int consecutiveFailures() {
        return consecutiveFailures.get();
    }
}
