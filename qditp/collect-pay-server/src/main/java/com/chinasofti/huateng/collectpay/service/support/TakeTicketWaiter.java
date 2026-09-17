package com.chinasofti.huateng.collectpay.service.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 取票授权的「挂起等待器」。 */
@Slf4j
@Component
public class TakeTicketWaiter {

    private final Map<String, CompletableFuture<Void>> waiters = new ConcurrentHashMap<>();

    private final AtomicInteger waiting = new AtomicInteger();

    /** 全局挂起上限，超限直接退回「立即返回」的原有行为，防止设备异常时把线程占满。 */
    @Value("${tvm.takeTicket.maxWaiting:500}")
    private int maxWaiting;

    /** 二维码三要素即等待键，与 {@code selectByDeviceAndQrcode} 的查询条件一一对应。 */
    public static String key(String deviceId, String qrcodeGenDate, String randomFact) {
        return deviceId + '|' + qrcodeGenDate + '|' + randomFact;
    }

    /** 占用一个挂起额度；返回 false 表示已超限，调用方 MUST 走原来的立即返回。 */
    public boolean tryAcquire() {
        if (waiting.incrementAndGet() > maxWaiting) {
            waiting.decrementAndGet();
            log.warn("取票授权挂起数已达上限, maxWaiting={}", maxWaiting);
            return false;
        }
        return true;
    }

    public void release() {
        waiting.decrementAndGet();
    }

    /**
     * 等待指定二维码被激活。
     *
     * @return true 表示等到了激活事件；false 表示本次时间片内没有事件，调用方回查数据库兜底
     */
    public boolean await(String key, long timeoutMillis) {
        if (timeoutMillis <= 0) {
            return false;
        }
        CompletableFuture<Void> future = waiters.computeIfAbsent(key, k -> new CompletableFuture<>());
        try {
            future.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 激活成功后唤醒等待者；没有等待者时什么都不做。 */
    public void signal(String key) {
        CompletableFuture<Void> future = waiters.remove(key);
        if (future != null && future.complete(null)) {
            log.info("取票授权等待者已被激活唤醒, key={}", key);
        }
    }

    /** 等待结束后清理，避免 map 里堆积已失效的键。 */
    public void discard(String key) {
        waiters.remove(key);
    }
}
