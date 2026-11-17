package com.chinasofti.huateng.alipay.paysign.controller.internal;

import com.chinasofti.huateng.alipay.paysign.service.AlipayTerminationInternalService;
import com.chinasofti.huateng.alipay.paysign.service.AlipayTerminationService;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/internal/alipay/termination")
public class AlipayTerminationInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayTerminationInternalController.class);

    private final AlipayTerminationInternalService alipayTerminationInternalService;
    private final AlipayTerminationService alipayTerminationService;

    public AlipayTerminationInternalController(AlipayTerminationInternalService alipayTerminationInternalService,
                                               AlipayTerminationService alipayTerminationService) {
        this.alipayTerminationInternalService = alipayTerminationInternalService;
        this.alipayTerminationService = alipayTerminationService;
    }


    @PostMapping("/process")
    public AlipayProcessTerminationRespDTO processTermination(
            @RequestBody(required = false) AlipayProcessTerminationReqDTO request) {
        log.info("收到支付宝出行销卡批处理请求, request={}", request);
        return alipayTerminationInternalService.processTermination(request);
    }


    @GetMapping("/execute")
    public AlipayCommonResponse executeTermination(@RequestParam String agreementCode) {
        log.info("收到执行解约请求: agreementCode={}", agreementCode);
        AlipayCommonResponse response = alipayTerminationService.executeTermination(agreementCode);
        log.info("执行解约响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }
}
