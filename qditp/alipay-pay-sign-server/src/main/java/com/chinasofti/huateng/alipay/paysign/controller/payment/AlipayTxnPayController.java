package com.chinasofti.huateng.alipay.paysign.controller.payment;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/payment")
public class AlipayTxnPayController {

    private static final Logger log = LoggerFactory.getLogger(AlipayTxnPayController.class);

    private final AlipayPayRequestService alipayPayRequestService;

    public AlipayTxnPayController(AlipayPayRequestService alipayPayRequestService) {
        this.alipayPayRequestService = alipayPayRequestService;
    }

    @PostMapping("/requestPay")
    public AlipayTripRequestPayRespDTO requestPay(@RequestBody AlipayTripRequestPayReqDTO request) {
        log.info("收到支付申请（新链路）: orderNo={}", request != null ? request.getOrderNo() : null);
        AlipayTripRequestPayRespDTO response = alipayPayRequestService.requestPay(request);
        log.info("支付申请响应结果（新链路）：retCode={}", response != null ? response.getRetCode() : "null");
        return response;
    }
}
