package com.chinasofti.huateng.heartbeat.controller;

import com.chinasofti.huateng.heartbeat.model.HeartbeatRequest;
import com.chinasofti.huateng.heartbeat.model.HeartbeatResponse;
import com.chinasofti.huateng.heartbeat.service.DeviceHeartbeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * TVM（自动售票机）心跳接口
 * 兼容原 IF2A-04 路径：/itptvm/ci/tvm/notiDeviceHeard
 */
@RestController
@RequestMapping("/heartbeat/tvm")
public class TvmHeartbeatController {
    private static final Logger log = LoggerFactory.getLogger(TvmHeartbeatController.class);
    private static final String CHANNEL = "02";

    private final DeviceHeartbeatService heartbeatService;

    public TvmHeartbeatController(DeviceHeartbeatService heartbeatService) {
        this.heartbeatService = heartbeatService;
    }

    @PostMapping({"/notiDeviceHeard", "/deviceHeartbeat"})
    public HeartbeatResponse notiDeviceHeard(@ModelAttribute HeartbeatRequest request) {
        String deviceId = request == null ? null : request.getDeviceId();
        log.debug("TVM设备心跳, deviceId={}", deviceId);
        if (request != null) {
            heartbeatService.recordHeartbeat(CHANNEL, deviceId, null);
        }
        return HeartbeatResponse.success();
    }
}
