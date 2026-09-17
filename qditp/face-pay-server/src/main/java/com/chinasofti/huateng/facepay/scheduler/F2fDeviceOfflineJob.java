package com.chinasofti.huateng.facepay.scheduler;

import com.chinasofti.huateng.facepay.service.F2fDeviceHeartbeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 设备离线判定任务。无分布式锁，face-pay-server MUST 单副本。 */
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
