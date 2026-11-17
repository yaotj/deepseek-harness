package com.chinasofti.huateng.alipay.paysign.controller.legacy;

import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.impl.payment.PaymentQueryService;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/payment")
public class AlipayTripPaymentController {

    private static final Logger log = LoggerFactory.getLogger(AlipayTripPaymentController.class);
    private final PaymentQueryService paymentQueryService;

    public AlipayTripPaymentController(PaymentQueryService paymentQueryService) {
        this.paymentQueryService = paymentQueryService;
    }








    @PostMapping("/findTravelDetail")
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(@RequestBody AlipayTripFindTravelDetailReqDTO request) {
        log.info("查询乘车记录详情: orderNo={}", request != null ? request.getOrderNo() : null);
        AlipayTripFindTravelDetailRespDTO response = paymentQueryService.findTravelDetail(request);
        log.info("查询乘车记录详情响应结果：{}", response != null ? response.getRetMsg() : "null");
        return response;
    }
}
