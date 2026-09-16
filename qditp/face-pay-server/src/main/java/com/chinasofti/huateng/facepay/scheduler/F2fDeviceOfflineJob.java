package com.chinasofti.huateng.facepay.scheduler;

import com.chinasofti.huateng.facepay.service.F2fDeviceHeartbeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 设备离线判定任务。心跳只负责「我还在」，判定「谁不在了」必须靠扫表——
 * 设备掉线时不会发一条「我下线了」的报文。
 *
 * <p>超时窗口 = {@code offlineTimeoutSeconds}，默认 300 秒（按 1 分钟心跳周期的 5 倍容忍）。
 * 这个倍数<b>不能压太紧</b>：TVM 在出票高峰期心跳可能延迟，误判离线会污染运营看板。</p>
 *
 * <p>本任务是纯本地 UPDATE，没有网络调用，也不需要逐条处理——
 * {@code markOfflineByDeadline} 一条 SQL 批量搞定，且 WHERE 带 {@code ONLINE_FLAG='1'}，
 * 多副本重复执行只是第二次命中 0 行。</p>
 */
@Component
public class F2fDeviceOfflineJob {

    private static final Logger log = LoggerFactory.getLogger(F2fDeviceOfflineJob.class);

    private final F2fDeviceHeartbeatService heartbeatService;

    private final long offlineTimeoutSeconds;

    public F2fDeviceOfflineJob(F2fDeviceHeartbeatService heartbeatService,
                               @Value("${f2f.device.offlineTimeoutSeconds:300}") long offlineTimeoutSeconds) {
        this.heartbeatService = heartbeatService;
        this.offlineTimeoutSeconds = offlineTimeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${f2f.device.offlineScanIntervalMs:60000}",
            initialDelayString = "${f2f.device.offlineScanInitialDelayMs:60000}")
    public void markTimeoutDevicesOffline() {
        try {
            LocalDateTime deadline = LocalDateTime.now().minusSeconds(offlineTimeoutSeconds);
            heartbeatService.markTimeoutDevicesOffline(deadline);
        } catch (RuntimeException e) {
            log.error("设备离线判定任务异常", e);
        }
    }
}
