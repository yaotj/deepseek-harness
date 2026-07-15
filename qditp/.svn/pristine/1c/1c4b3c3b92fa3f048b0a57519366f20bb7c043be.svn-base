package com.chinasofti.huateng.alipay.paysign.controller;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPaySignService;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行支付 Controller。
 */
@RestController
@RequestMapping("/api/payment")
public class AlipayTripPaymentController {

    private final AlipayPaySignService alipayPaySignService;

    public AlipayTripPaymentController(AlipayPaySignService alipayPaySignService) {
        this.alipayPaySignService = alipayPaySignService;
    }

    /**
     * 支付申请。
     */
    @PostMapping("/requestPay")
    public AlipayTripRequestPayRespDTO requestPay(@RequestBody AlipayTripRequestPayReqDTO request) {
        return alipayPaySignService.requestPay(request);
    }

    /**
     * 支付结果查询。
     */
    @PostMapping("/payQuery")
    public AlipayTripPayQueryRespDTO payQuery(@RequestBody AlipayTripPayQueryReqDTO request) {
        return alipayPaySignService.payQuery(request);
    }

    /**
     * 退款申请。
     */
    @PostMapping("/requestRefund")
    public AlipayTripRequestRefundRespDTO requestRefund(@RequestBody AlipayTripRequestRefundReqDTO request) {
        return alipayPaySignService.requestRefund(request);
    }

    /**
     * 支付结果回调。
     */
    @PostMapping("/payNotify")
    public AlipayCommonResponse payNotify(@RequestBody AlipayTripPayNotifyReqDTO request) {
        boolean success = alipayPaySignService.handlePayNotify(request);
        return success ? AlipayCommonResponse.success() : AlipayCommonResponse.fail("处理失败");
    }
}
