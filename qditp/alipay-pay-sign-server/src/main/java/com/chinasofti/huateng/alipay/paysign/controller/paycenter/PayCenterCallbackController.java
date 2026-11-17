package com.chinasofti.huateng.alipay.paysign.controller.paycenter;

import com.chinasofti.huateng.alipay.paysign.service.AlipayPayCallbackService;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/payment")
public class PayCenterCallbackController {

    private static final Logger log = LoggerFactory.getLogger(PayCenterCallbackController.class);
    private final AlipayPayCallbackService alipayPayCallbackService;

    public PayCenterCallbackController(AlipayPayCallbackService alipayPayCallbackService) {
        this.alipayPayCallbackService = alipayPayCallbackService;
    }


    @PostMapping("/payNotify")
    public AlipayCommonResponse payNotify(@RequestBody AlipayTripPayNotifyReqDTO request) {
        log.info("收到支付回调: orderNo={}", request != null ? request.getOrderNo() : null);
        AlipayCommonResponse response = alipayPayCallbackService.handlePayNotify(request);
        log.info("支付回调响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }


    @PostMapping("/refundNotify")
    public AlipayCommonResponse refundNotify(@RequestBody AlipayTripRefundNotifyReqDTO request) {
        log.info("收到退款回调: orderNo={}, refundResult={}",
                request != null ? request.getOrderNo() : null,
                request != null ? request.getRefundResult() : null);
        AlipayCommonResponse response = alipayPayCallbackService.handleRefundNotify(request);
        log.info("退款回调响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }
}
