package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.mapper.F2fDeviceStatusMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 设备心跳。本类刻意不带 {@code @Transactional}（单条 SQL 自动提交即可），NEVER 加。 */
@Service
public class F2fDeviceHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(F2fDeviceHeartbeatService.class);

    private final F2fDeviceStatusMapper deviceStatusMapper;

    public F2fDeviceHeartbeatService(F2fDeviceStatusMapper deviceStatusMapper) {
        this.deviceStatusMapper = deviceStatusMapper;
    }

    /**
     * 记一次心跳。
     *
     * @param channel     渠道码，见 {@code F2fChannel}
     * @param deviceId    设备号，可能为空
     * @param stationCode 车站编码，可空；非空时覆盖已有值
     * @return true 表示已落库，false 表示因设备号缺失被跳过
     */
    public boolean recordHeartbeat(String channel, String deviceId, String stationCode) {
        if (deviceId == null || deviceId.isBlank()) {
            log.warn("设备心跳缺少 deviceId，跳过落库, channel={}", channel);
            return false;
        }
        int merged = deviceStatusMapper.mergeHeartbeat(channel, deviceId, stationCode, LocalDateTime.now());
        log.debug("设备心跳已记录, channel={}, deviceId={}, mergedRows={}", channel, deviceId, merged);
        return merged > 0;
    }

    /**
     * 把心跳超时的在线设备批量置离线，供 {@code @Scheduled} 调用。
     *
     * @return 实际被置离线的设备数
     */
    public int markTimeoutDevicesOffline(LocalDateTime deadline) {
        int offline = deviceStatusMapper.markOfflineByDeadline(deadline);
        if (offline > 0) {
            log.warn("心跳超时置离线设备 {} 台, deadline={}", offline, deadline);
        }
        return offline;
    }
}
