package com.chinasofti.huateng.facepay.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDateTime;

/** 退款收口的「退避 + 放弃」策略，把原先散在 {@link F2fRefundService} 构造里的 5 个 {@code @Value} 收成一个对象（2026-09-16）。 */
@ConfigurationProperties(prefix = "f2f.refund")
public class F2fRefundRetryPolicy {

    /** 移位次数上限，避免 {@code long << n} 溢出成负数（60 秒 × 2^20 已远超封顶值）。 */
    private static final int MAX_SHIFT = 20;

    /** 受理后第一次回查的延迟（秒）。 */
    private long firstQueryDelaySeconds = 60;

    /** 退避基数（秒），每失败一次翻倍。 */
    private long backoffBaseSeconds = 300;

    /** 退避封顶（秒）。 */
    private long backoffMaxSeconds = 3600;

    /** 查询次数上限，达到即转 {@code MANUAL}。 */
    private int maxQueryTimes = 30;

    /** 自动收口的时间窗（小时）。 */
    private long giveUpAfterHours = 24;

    /**
     * 退款单落库时的首次回查时刻。
     *
     * @param requestTms 退款请求时刻，为 null 时按「现在」起算（原实现同此口径）
     */
    public LocalDateTime firstQueryTms(LocalDateTime requestTms) {
        LocalDateTime base = requestTms == null ? LocalDateTime.now() : requestTms;
        return base.plusSeconds(firstQueryDelaySeconds);
    }

    /**
     * 按已失败次数算下次查询时刻：间隔 {@code base * 2^n}，封顶 {@code max}。
     *
     * @param failedTimes 已失败次数，null 或负数按 0 处理
     */
    public LocalDateTime nextQueryTms(Integer failedTimes) {
        int n = failedTimes == null || failedTimes < 0 ? 0 : Math.min(failedTimes, MAX_SHIFT);
        long delay = Math.min(backoffBaseSeconds << n, backoffMaxSeconds);
        return LocalDateTime.now().plusSeconds(delay);
    }

    /** 查询次数是否已用尽。 */
    public boolean timesExhausted(Integer retryTimes) {
        return queriedTimes(retryTimes) >= maxQueryTimes;
    }

    /** 是否已超出自动收口时间窗。 */
    public boolean windowExpired(LocalDateTime requestTms) {
        return requestTms != null && requestTms.isBefore(LocalDateTime.now().minusHours(giveUpAfterHours));
    }

    /** 已查询次数的 null 归一，供日志打印用。 */
    public int queriedTimes(Integer retryTimes) {
        return retryTimes == null ? 0 : retryTimes;
    }

    public long getFirstQueryDelaySeconds() {
        return firstQueryDelaySeconds;
    }

    public void setFirstQueryDelaySeconds(long firstQueryDelaySeconds) {
        this.firstQueryDelaySeconds = firstQueryDelaySeconds;
    }

    public long getBackoffBaseSeconds() {
        return backoffBaseSeconds;
    }

    public void setBackoffBaseSeconds(long backoffBaseSeconds) {
        this.backoffBaseSeconds = backoffBaseSeconds;
    }

    public long getBackoffMaxSeconds() {
        return backoffMaxSeconds;
    }

    public void setBackoffMaxSeconds(long backoffMaxSeconds) {
        this.backoffMaxSeconds = backoffMaxSeconds;
    }

    public int getMaxQueryTimes() {
        return maxQueryTimes;
    }

    public void setMaxQueryTimes(int maxQueryTimes) {
        this.maxQueryTimes = maxQueryTimes;
    }

    public long getGiveUpAfterHours() {
        return giveUpAfterHours;
    }

    public void setGiveUpAfterHours(long giveUpAfterHours) {
        this.giveUpAfterHours = giveUpAfterHours;
    }
}
