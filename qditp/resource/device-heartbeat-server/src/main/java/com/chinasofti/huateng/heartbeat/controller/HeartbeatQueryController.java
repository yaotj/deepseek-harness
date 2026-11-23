package com.chinasofti.huateng.heartbeat.controller;

import com.chinasofti.huateng.heartbeat.model.DeviceHeartbeatStatus;
import com.chinasofti.huateng.heartbeat.service.DeviceHeartbeatService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 设备心跳状态查询接口（运营端使用）
 */
@RestController
@RequestMapping("/api/heartbeat")
public class HeartbeatQueryController {

    private final DeviceHeartbeatService heartbeatService;

    public HeartbeatQueryController(DeviceHeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    /**
     * 设备汇总统计
     */
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        return heartbeatService.getSummary();
    }

    /**
     * 按渠道查询设备列表
     */
    @GetMapping("/devices")
    public List<DeviceHeartbeatStatus> getDevices(
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String onlineFlag) {
        return heartbeatService.getDevicesByChannel(channel, onlineFlag);
    }

    /**
     * 查询超时设备（用于定时任务）
     */
    @GetMapping("/timeout")
    public List<DeviceHeartbeatStatus> getTimeoutDevices(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime deadline,
            @RequestParam(defaultValue = "100") int limit) {
        return heartbeatService.getTimeoutDevices(deadline, limit);
    }
}
