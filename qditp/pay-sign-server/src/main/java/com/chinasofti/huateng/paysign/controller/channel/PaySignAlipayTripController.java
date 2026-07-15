package com.chinasofti.huateng.paysign.controller.channel;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.service.PaySignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付宝出行-渠道接口。
 * <p>
 * 对应接口规范 /channel/* 路径，与 FepAlipayTripController 路径对齐。
 * </p>
 */
@RestController
@RequestMapping("/channel")
public class PaySignAlipayTripController {
    private static final Logger log = LoggerFactory.getLogger(PaySignAlipayTripController.class);

    @Autowired
    private PaySignService paySignService;

    @PostMapping("/requestContractAdvisory")
    public RequestContractAdvisoryRespDTO requestContractAdvisory(@RequestBody RequestContractAdvisoryReqDTO request) {
        log.info("接收到支付宝信用能力咨询报文: {}", request);
        // 支付宝出行专属入口，固定签约渠道为 ALIPAY
        return paySignService.requestContractAdvisory(request, SignChannelEnum.ALIPAY.getCode());
    }

    @PostMapping("/requestContractResult")
    public RequestContractResultRespDTO requestContractResult(@RequestBody RequestContractResultReqDTO request) {
        log.info("接收到支付宝签约结果咨询报文: {}", request);
        // 支付宝出行专属入口，固定签约渠道为 ALIPAY
        return paySignService.requestContractResult(request, SignChannelEnum.ALIPAY.getCode());
    }

    @PostMapping("/requestTermination")
    public RequestTerminationRespDTO requestTermination(@RequestBody RequestTerminationReqDTO request) {
        log.info("接收到支付宝请求解约报文: {}", request);
        // 支付宝出行专属入口，固定签约渠道为 ALIPAY
        return paySignService.requestTermination(request, SignChannelEnum.ALIPAY.getCode());
    }

    /**
     * 支付宝出行-添加签约信息。
     * <p>
     * 对应接口规范 /channel/addContract，与 FepAlipayTripController 路径对齐。
     * 支付宝渠道固定签约渠道为 ALIPAY。
     * </p>
     */
    @PostMapping("/addContract")
    public RequestSignInfoResult addContract(@RequestBody AlipayTripAddContractReqDTO request) {
        log.info("接收到支付宝添加签约信息报文: {}", request);
        // 支付宝出行专属入口，固定签约渠道为 ALIPAY
        return paySignService.alipayTripRequestSignInfo(request);
    }
}
