package com.chinasofti.huateng.alipay.paysign.controller;

import com.chinasofti.huateng.alipay.paysign.service.AlipayTerminationInternalService;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行销卡内部接口控制器。
 *
 * <p>与 pay-sign 的 {@code /internal/termination/process} 同形：调用方反复调用直到
 * {@code scanned} 为 0 完成排空，服务端单次只处理一批。</p>
 */
@RestController
@RequestMapping("/internal/alipay/termination")
public class AlipayTerminationInternalController {

    private static final Logger log = LoggerFactory.getLogger(AlipayTerminationInternalController.class);

    @Autowired
    private AlipayTerminationInternalService alipayTerminationInternalService;

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
