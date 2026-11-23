package com.chinasofti.huateng.heartbeat.service;

import com.chinasofti.huateng.heartbeat.mapper.DeviceHeartbeatLogMapper;
import com.chinasofti.huateng.heartbeat.mapper.DeviceHeartbeatStatusMapper;
import com.chinasofti.huateng.heartbeat.model.DeviceHeartbeatLog;
import com.chinasofti.huateng.heartbeat.model.DeviceHeartbeatStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 设备心跳统一处理服务
 */
@Service
public class DeviceHeartbeatService {
    private static final Logger log = LoggerFactory.getLogger(DeviceHeartbeatService.class);

    private static final int STATION_CODE_LENGTH = 4;

    private final DeviceHeartbeatLogMapper logMapper;
    private final DeviceHeartbeatStatusMapper statusMapper;

    public DeviceHeartbeatService(DeviceHeartbeatLogMapper logMapper,
                                  DeviceHeartbeatStatusMapper statusMapper) {
        this.logMapper = logMapper;
        this.statusMapper = statusMapper;
    }

    /**
     * 记录设备心跳
     *
     * @param channel  渠道编码（01-APP, 02-TVM, 03-BOM, 04-AGM）
     * @param deviceId 设备ID
     * @param bizData  业务数据快照（可为null）
     */
    @Transactional
    public void recordHeartbeat(String channel, String deviceId, String bizData) {
        if (deviceId == null || deviceId.isBlank()) {
            log.warn("设备心跳缺少 deviceId，跳过, channel={}", channel);
            return;
        }

        String stationCode = extractStationCode(deviceId);
        LocalDateTime now = LocalDateTime.now();

        // 1. 写入历史日志表
        DeviceHeartbeatLog logEntry = new DeviceHeartbeatLog();
        logEntry.setChannel(channel);
        logEntry.setDeviceId(deviceId);
        logEntry.setStationCode(stationCode);
        logEntry.setHeartbeatTms(now);
        logEntry.setReqBizData(bizData);
        logMapper.insertLog(logEntry);

        // 2. 更新最新状态表
        statusMapper.mergeHeartbeat(channel, deviceId, stationCode, now);

        log.debug("设备心跳已记录, channel={}, deviceId={}, stationCode={}", channel, deviceId, stationCode);
    }

    /**
     * 从设备ID提取站码（前4位）
     */
    public static String extractStationCode(String deviceId) {
        if (deviceId == null || deviceId.length() < STATION_CODE_LENGTH) {
            return null;
        }
        return deviceId.substring(0, STATION_CODE_LENGTH);
    }

    /**
     * 查询超时设备（用于定时任务置离线）
     */
    public List<DeviceHeartbeatStatus> getTimeoutDevices(LocalDateTime deadline, int limit) {
        return statusMapper.selectTimeoutDevices(deadline, limit);
    }

    /**
     * 批量置离线
     */
    public int markOfflineByDeadline(LocalDateTime deadline) {
        int count = statusMapper.markOfflineByDeadline(deadline);
        if (count > 0) {
            log.warn("心跳超时置离线设备 {} 台, deadline={}", count, deadline);
        }
        return count;
    }

    /**
     * 按渠道查询设备列表
     */
    public List<DeviceHeartbeatStatus> getDevicesByChannel(String channel, String onlineFlag) {
        return statusMapper.selectByChannel(channel, onlineFlag);
    }

    /**
     * 查询设备汇总统计
     */
    public Map<String, Object> getSummary() {
        return statusMapper.selectSummary();
    }
}
