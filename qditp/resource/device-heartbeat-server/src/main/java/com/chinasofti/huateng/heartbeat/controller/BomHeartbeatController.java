package com.chinasofti.huateng.heartbeat.controller;

import com.chinasofti.huateng.heartbeat.model.HeartbeatRequest;
import com.chinasofti.huateng.heartbeat.model.HeartbeatResponse;
import com.chinasofti.huateng.heartbeat.service.DeviceHeartbeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * BOM（半自动售票机）心跳接口
 * 路径 = 老服务 face-pay-server 的 BomOrderController 路径（照抄，@RequestMapping 多值）：
 *   /itpbom/ci/bom/{notiDeviceHeard,deviceHeartbeat}
 *   /itptvm/ci/bom/{notiDeviceHeard,deviceHeartbeat}
 * 网关 exact 路由把这两条老路径直接劫持到本服务，无 rewrite。
 */
@RestController
@RequestMapping({"/itpbom/ci/bom", "/itptvm/ci/bom"})
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
            heartbeatService.recordHeartbeat(CHANNEL, deviceId);
        }
        return HeartbeatResponse.success();
    }
}
