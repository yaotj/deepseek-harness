package com.chinasofti.huateng.alipay.paysign.controller;

import com.chinasofti.huateng.alipay.paysign.service.AlipayTerminationInternalService;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 支付宝出行销卡内部接口控制器。 */
@RestController
@RequestMapping("/internal/alipay/termination")
public class AlipayTerminationInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayTerminationInternalController.class);

    private final AlipayTerminationInternalService alipayTerminationInternalService;

    public AlipayTerminationInternalController(AlipayTerminationInternalService alipayTerminationInternalService) {
        this.alipayTerminationInternalService = alipayTerminationInternalService;
    }

    /**
     * 支付宝出行销卡批处理：扫一批 PENDING 的销卡登记逐条执行。
     */
    @PostMapping("/process")
    public AlipayProcessTerminationRespDTO processTermination(
            @RequestBody(required = false) AlipayProcessTerminationReqDTO request) {
        log.info("收到支付宝出行销卡批处理请求, request={}", request);
        return alipayTerminationInternalService.processTermination(request);
    }
}
