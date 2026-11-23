package com.chinasofti.huateng.heartbeat.controller;

import com.chinasofti.huateng.heartbeat.model.HeartbeatRequest;
import com.chinasofti.huateng.heartbeat.model.HeartbeatResponse;
import com.chinasofti.huateng.heartbeat.service.DeviceHeartbeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * BOM（半自动售票机）心跳接口
 * 兼容原路径：/itpbom/ci/bom/notiDeviceHeard
 */
@RestController
@RequestMapping("/heartbeat/bom")
public class BomHeartbeatController {
    private static final Logger log = LoggerFactory.getLogger(BomHeartbeatController.class);
    private static final String CHANNEL = "03";

    private final DeviceHeartbeatService heartbeatService;

    public BomHeartbeatController(DeviceHeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @PostMapping({"/notiDeviceHeard", "/deviceHeartbeat"})
    public HeartbeatResponse notiDeviceHeard(@ModelAttribute HeartbeatRequest request) {
        String deviceId = request == null ? null : request.getDeviceId();
        log.debug("BOM设备心跳, deviceId={}", deviceId);
        if (request != null) {
            heartbeatService.recordHeartbeat(CHANNEL, deviceId, null);
        }
        return HeartbeatResponse.success();
    }
}
