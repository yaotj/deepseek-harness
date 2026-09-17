package com.chinasofti.huateng.account.controller.ci.channel;

import com.chinasofti.huateng.account.service.AlipayTripRegistrationService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行-开卡申请（规范 3.69）在 account-server 侧的实现，<b>当前保留但不在链路上</b>。
 */
@RestController
public class FepAlipayTripRequestApplicationController {
    private static final Logger log = LoggerFactory.getLogger(FepAlipayTripRequestApplicationController.class);

    private final AlipayTripRegistrationService alipayTripRegistrationService;

    /**
     * 构造器注入（ADR-D37）。
     */
    public FepAlipayTripRequestApplicationController(AlipayTripRegistrationService alipayTripRegistrationService) {
        this.alipayTripRegistrationService = alipayTripRegistrationService;
    }

    @PostMapping("/channel/requestApplication")
    public AlipayTripRequestApplicationRespDTO requestApplication(@RequestBody AlipayTripRequestApplicationReqDTO request) {
        log.info("接收到支付宝出行-开卡申请报文: {}", request);
        return alipayTripRegistrationService.alipayTripRequestApplication(request);
    }
}
