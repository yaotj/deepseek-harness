package com.chinasofti.huateng.collectpay.controller.ci.app;

import com.chinasofti.huateng.collectpay.model.request.PayQueryReqDTO;
import com.chinasofti.huateng.collectpay.model.request.RefundQueryReqDTO;
import com.chinasofti.huateng.collectpay.model.request.RequestPayReqDTO;
import com.chinasofti.huateng.collectpay.model.request.RequestRefundReqDTO;
import com.chinasofti.huateng.collectpay.model.response.PayQueryRespDTO;
import com.chinasofti.huateng.collectpay.model.response.RequestPayRespDTO;
import com.chinasofti.huateng.collectpay.model.response.RequestRefundRespDTO;
import com.chinasofti.huateng.collectpay.model.response.RefundQueryRespDTO;
import com.chinasofti.huateng.collectpay.service.CollectPayService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 取票支付服务接口（APP端）。
 */
@RestController
@RequestMapping("/ci/app")
public class CollectPayController {
    private static final Logger log = LoggerFactory.getLogger(CollectPayController.class);

    @Autowired
    private CollectPayService collectPayService;

    /**
     * IF8A-09 请求支付。
     */
    @PostMapping("/requestPay")
    public RequestPayRespDTO requestPay(@RequestBody RequestPayReqDTO request) {
        log.info("接收到请求支付接口报文: {}", request);
        return collectPayService.requestPay(request);
    }

    /**
     * IF8A-10 支付查询。
     */
    @PostMapping("/payQuery")
    public PayQueryRespDTO payQuery(@RequestBody PayQueryReqDTO request) {
        log.info("接收到支付查询接口报文: {}", request);
        return collectPayService.payQuery(request);
    }

    /**
     * IF8A-12 请求退款。
     */
    @PostMapping("/requestRefund")
    public RequestRefundRespDTO requestRefund(@RequestBody RequestRefundReqDTO request) {
        log.info("接收到请求退款接口报文: {}", request);
        return collectPayService.requestRefund(request);
    }

    /**
     * IF8A-13 退款查询。
     */
    @PostMapping("/refundQuery")
    public RefundQueryRespDTO refundQuery(@RequestBody RefundQueryReqDTO request) {
        log.info("接收到退款查询接口报文: {}", request);
        return collectPayService.refundQuery(request);
    }
}
