package com.chinasofti.huateng.paysign.controller.notify;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.ReceiveTerminationResultReqDTO;
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
 * 支付宝出行-通知回调控制器。
 * <p>
 * 对应接口规范 /notify/* 路径，与 FepAlipayTripNotifyController 路径对齐。
 * 当前支付宝采用同步确认模式，以下接口为预留，暂不启用。
 * </p>
 */
@RestController
@RequestMapping("/notify")
public class PaySignAlipayTripNotifyController {
    private static final Logger log = LoggerFactory.getLogger(PaySignAlipayTripNotifyController.class);

    @Autowired
    private PaySignService paySignService;

    /**
     * 支付宝签约结果通知（预留，当前采用同步确认模式，无需回调）。
     *
     * <p>若后续支付平台定义支付宝签约结果回调，可启用此接口。</p>
     */
    @PostMapping("/receiveSignResult")
    public Object receiveSignResult(@RequestBody ReceiveSignResultReqDTO request) {
        log.info("接收到支付宝签约结果通知报文: {}", request);
        return paySignService.receiveSignResult(request, SignChannelEnum.ALIPAY.getCode());
    }

    /**
     * 支付宝解约结果通知（预留，当前采用同步确认模式，无需回调）。
     *
     * <p>若后续支付平台定义支付宝解约结果回调，可启用此接口。</p>
     */
    @PostMapping("/receiveTerminationResult")
    public Object receiveTerminationResult(@RequestBody ReceiveTerminationResultReqDTO request) {
        log.info("接收到支付宝解约结果通知报文: {}", request);
        return paySignService.receiveTerminationResult(request, SignChannelEnum.ALIPAY.getCode());
    }
}
