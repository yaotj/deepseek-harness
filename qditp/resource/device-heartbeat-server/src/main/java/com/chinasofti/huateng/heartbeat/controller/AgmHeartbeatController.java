package com.chinasofti.huateng.heartbeat.controller;

import com.chinasofti.huateng.heartbeat.model.HeartbeatRequest;
import com.chinasofti.huateng.heartbeat.model.HeartbeatResponse;
import com.chinasofti.huateng.heartbeat.service.DeviceHeartbeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * AGM（闸机）心跳接口
 * 兼容原 IF1A-03 路径：/fep-dev/ci/agm/deviceHeartbeat
 */
@RestController
@RequestMapping("/heartbeat/agm")
public class AgmHeartbeatController {
    private static final Logger log = LoggerFactory.getLogger(AgmHeartbeatController.class);
    private static final String CHANNEL = "04";

    private final DeviceHeartbeatService heartbeatService;

    public AgmHeartbeatController(DeviceHeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @PostMapping({"/deviceHeartbeat", "/notiDeviceHeard"})
    public HeartbeatResponse deviceHeartbeat(@ModelAttribute HeartbeatRequest request) {
        String deviceId = request == null ? null : request.getDeviceId();
        log.info("IF1A-03 AGM设备心跳, deviceId={}", deviceId);
        if (request != null) {
            heartbeatService.recordHeartbeat(CHANNEL, deviceId, request.getBizData());
        }
        return HeartbeatResponse.success();
    }
}
