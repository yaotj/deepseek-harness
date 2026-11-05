package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.mapper.F2fDeviceStatusMapper;
import com.chinasofti.huateng.facepay.support.F2fDeviceId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class F2fDeviceHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(F2fDeviceHeartbeatService.class);

    private final F2fDeviceStatusMapper deviceStatusMapper;

    public F2fDeviceHeartbeatService(F2fDeviceStatusMapper deviceStatusMapper) {
        this.deviceStatusMapper = deviceStatusMapper;
    }

    public boolean recordHeartbeat(String channel, String deviceId, String stationCode) {
        if (deviceId == null || deviceId.isBlank()) {
            log.warn("设备心跳缺少 deviceId，跳过落库, channel={}", channel);
            return false;
        }
        String effectiveStationCode = stationCode == null || stationCode.isBlank()
                ? F2fDeviceId.stationCode(deviceId) : stationCode.trim();
        int merged = deviceStatusMapper.mergeHeartbeat(channel, deviceId, effectiveStationCode, LocalDateTime.now());
        log.debug("设备心跳已记录, channel={}, deviceId={}, stationCode={}, mergedRows={}",
                channel, deviceId, effectiveStationCode, merged);
        return merged > 0;
    }

    public int markTimeoutDevicesOffline(LocalDateTime deadline) {
        int offline = deviceStatusMapper.markOfflineByDeadline(deadline);
        if (offline > 0) {
            log.warn("心跳超时置离线设备 {} 台, deadline={}", offline, deadline);
        }
        return offline;
    }
}
