package com.chinasofti.huateng.paysign.controller.channel;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.service.PaySignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/channel")
public class PaySignAlipayTripController {
    private static final Logger log = LoggerFactory.getLogger(PaySignAlipayTripController.class);

    private final PaySignService paySignService;

    public PaySignAlipayTripController(PaySignService paySignService) {
        this.paySignService = paySignService;
    }

    @PostMapping("/requestContractAdvisory")
    public RequestContractAdvisoryRespDTO requestContractAdvisory(@RequestBody RequestContractAdvisoryReqDTO request) {
        log.info("接收到支付宝信用能力咨询报文: {}", request);
        return paySignService.requestContractAdvisory(request, SignChannelEnum.ALIPAY.getCode());
    }

    @PostMapping("/requestContractResult")
    public RequestContractResultRespDTO requestContractResult(@RequestBody RequestContractResultReqDTO request) {
        log.info("接收到支付宝签约结果咨询报文: {}", request);
        return paySignService.requestContractResult(request, SignChannelEnum.ALIPAY.getCode());
    }

    @PostMapping("/requestTermination")
    public RequestTerminationRespDTO requestTermination(@RequestBody RequestTerminationReqDTO request) {
        log.info("接收到支付宝请求解约报文: {}", request);
        return paySignService.requestTermination(request, SignChannelEnum.ALIPAY.getCode());
    }

    @PostMapping("/addContract")
    public RequestSignInfoResult addContract(@RequestBody AlipayTripAddContractReqDTO request) {
        log.info("接收到支付宝添加签约信息报文: {}", request);
        return paySignService.alipayTripRequestSignInfo(request);
    }
}
