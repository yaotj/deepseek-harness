package com.chinasofti.huateng.facepay.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.LocalDateTime;

@ConfigurationProperties(prefix = "f2f.refund")
public class F2fRefundRetryPolicy {

    private static final int MAX_SHIFT = 20;

    private long firstQueryDelaySeconds = 60;

    private long backoffBaseSeconds = 300;

    private long backoffMaxSeconds = 3600;

    private int maxQueryTimes = 30;

    private long giveUpAfterHours = 24;

    public LocalDateTime firstQueryTms(LocalDateTime requestTms) {
        LocalDateTime base = requestTms == null ? LocalDateTime.now() : requestTms;
        return base.plusSeconds(firstQueryDelaySeconds);
    }

    public LocalDateTime nextQueryTms(Integer failedTimes) {
        int n = failedTimes == null || failedTimes < 0 ? 0 : Math.min(failedTimes, MAX_SHIFT);
        long delay = Math.min(backoffBaseSeconds << n, backoffMaxSeconds);
        return LocalDateTime.now().plusSeconds(delay);
    }

    public boolean timesExhausted(Integer retryTimes) {
        return queriedTimes(retryTimes) >= maxQueryTimes;
    }

    public boolean windowExpired(LocalDateTime requestTms) {
        return requestTms != null && requestTms.isBefore(LocalDateTime.now().minusHours(giveUpAfterHours));
    }

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
