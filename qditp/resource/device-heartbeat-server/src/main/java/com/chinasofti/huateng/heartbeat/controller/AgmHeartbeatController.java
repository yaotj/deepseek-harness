package com.chinasofti.huateng.heartbeat.controller;

import com.chinasofti.huateng.heartbeat.model.HeartbeatRequest;
import com.chinasofti.huateng.heartbeat.model.HeartbeatResponse;
import com.chinasofti.huateng.heartbeat.service.DeviceHeartbeatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * AGM（闸机）心跳接口
 * 路径 = 老服务 fep-dev-server 的 FepAgmController 路径（照抄）：
 *   /ci/agm/{deviceHeartbeat,notiDeviceHeard}
 * 网关 /itpagm/ 与 /fep-dev/ 两条前缀均 rewrite=/，服务实际收到 /ci/agm/...；
 * 网关 exact 路由把 /itpagm/ci/agm/* 与 /fep-dev/ci/agm/* rewrite 到本路径后劫持到本服务。
 */
@RestController
@RequestMapping("/ci/agm")
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
            heartbeatService.recordHeartbeat(CHANNEL, deviceId);
        }
        return HeartbeatResponse.success();
    }
}
