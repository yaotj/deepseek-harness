package com.chinasofti.huateng.heartbeat.controller;

import com.chinasofti.huateng.heartbeat.model.HeartbeatRequest;
import com.chinasofti.huateng.heartbeat.model.HeartbeatResponse;
import com.chinasofti.huateng.heartbeat.service.DeviceHeartbeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * TVM（自动售票机）心跳接口
 * 路径 = 老服务 face-pay-server 的 TvmOrderController 路径（照抄，@RequestMapping 多值）：
 *   /itptvm/ci/tvm/{notiDeviceHeard,deviceHeartbeat}
 *   /itpbom/ci/tvm/{notiDeviceHeard,deviceHeartbeat}
 * 网关 exact 路由把这两条老路径直接劫持到本服务，无 rewrite。
 */
@RestController
@RequestMapping({"/itptvm/ci/tvm", "/itpbom/ci/tvm"})
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
            heartbeatService.recordHeartbeat(CHANNEL, deviceId);
        }
        return HeartbeatResponse.success();
    }
}
