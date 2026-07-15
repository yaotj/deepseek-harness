package com.chinasofti.huateng.account.controller.ci.channel;

import com.chinasofti.huateng.account.service.AccountApplicationService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestApplicationRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FepAlipayTripRequestApplicationController {
    private static final Logger log = LoggerFactory.getLogger(FepAlipayTripRequestApplicationController.class);

    @Autowired
    private AccountApplicationService accountApplicationService;

    @PostMapping("/channel/requestApplication")
    public AlipayTripRequestApplicationRespDTO requestApplication(@RequestBody AlipayTripRequestApplicationReqDTO request) {
        log.info("接收到支付宝出行-开卡申请报文: {}", request);
        return accountApplicationService.alipayTripRequestApplication(request);
    }
}
