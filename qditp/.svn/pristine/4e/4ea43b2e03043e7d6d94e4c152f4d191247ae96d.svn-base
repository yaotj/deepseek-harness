package com.chinasofti.huateng.online.controller;

import com.chinasofti.huateng.online.model.BaseRespDTO;
import com.chinasofti.huateng.online.model.agm.AgmDtos;
import com.chinasofti.huateng.online.service.OnlineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ITP 与 AGM 对接控制器。
 * 对应规范第 7.4 章，负责闸机检票通知、密钥同步、二维码状态查询与设备心跳。
 */
@RestController
@RequestMapping("/ci/agm")
public class AgmController {
    private static final Logger log = LoggerFactory.getLogger(AgmController.class);

    @Autowired
    private OnlineService onlineService;

    /**
     * IF1A-01 闸机检票通知。
     * AGM 完成二维码校验和开门处理后，将结果准实时通知 ITP。
     */
    @PostMapping("/notiVerifyResult")
    public BaseRespDTO notiVerifyResult(@RequestBody AgmDtos.NotiVerifyResultReqDTO request,
                                        @RequestParam(value = "deviceId", required = false) String deviceId) {
        log.info("接收AGM检票结果通知: {}", request.getCardId());
        return onlineService.notiVerifyResult(request, deviceId);
    }

    /**
     * IF1A-02 密钥同步。
     * AGM 在开机或运营开始前拉取 ITP CA 公钥版本信息。
     */
    @PostMapping("/requestSynKeyList")
    public AgmDtos.RequestSynKeyListRespDTO requestSynKeyList(@RequestBody(required = false) AgmDtos.RequestSynKeyListReqDTO request) {
        log.info("接收AGM密钥同步请求");
        return onlineService.requestSynKeyList(request);
    }

    /**
     * IF1A-03 设备心跳。
     * AGM 定时上送心跳，平台记录最后在线时间。
     */
    @PostMapping("/notiDeviceHeard")
    public BaseRespDTO notiDeviceHeard(@RequestParam(value = "deviceId", required = false) String deviceId) {
        log.info("接收AGM设备心跳: {}", deviceId);
        return onlineService.deviceHeartbeat("04", deviceId);
    }

    /**
     * IF1A-04 查询票卡状态。
     * AGM 在检票前可调用，用于查询平台侧二维码票卡最新状态与锁定情况。
     */
    @PostMapping("/requestQrCodeStatus")
    public AgmDtos.RequestQrCodeStatusRespDTO requestQrCodeStatus(
            @RequestBody AgmDtos.RequestQrCodeStatusReqDTO request) {
        log.info("接收AGM二维码状态查询: {}", request.getCardId());
        return onlineService.requestQrCodeStatus(request);
    }
}
