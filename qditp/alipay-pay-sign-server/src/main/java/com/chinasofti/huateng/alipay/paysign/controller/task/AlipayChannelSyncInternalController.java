package com.chinasofti.huateng.alipay.paysign.controller.task;

import com.chinasofti.huateng.alipay.paysign.service.impl.channelsync.ChannelSyncCompensationService;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/internal/alipay/channelSync")
public class AlipayChannelSyncInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayChannelSyncInternalController.class);

    private final ChannelSyncCompensationService channelSyncCompensationService;

    public AlipayChannelSyncInternalController(ChannelSyncCompensationService channelSyncCompensationService) {
        this.channelSyncCompensationService = channelSyncCompensationService;
    }


    @PostMapping("/compensate")
    public AlipayCommonResponse compensate() {
        log.info("收到支付通道同步补偿请求");
        int succeeded = channelSyncCompensationService.compensate();
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("本轮收口 " + succeeded + " 条");
        return response;
    }
}
