package com.chinasofti.huateng.paysign.controller.notify;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.service.PaySignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 支付宝出行-通知回调控制器。 */
@RestController
@RequestMapping("/notify")
public class PaySignAlipayTripNotifyController {
    private static final Logger log = LoggerFactory.getLogger(PaySignAlipayTripNotifyController.class);

    private final PaySignService paySignService;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public PaySignAlipayTripNotifyController(PaySignService paySignService) {
        this.paySignService = paySignService;
    }

    /** 支付宝签约结果通知（预留，当前采用同步确认模式，无需回调）。 */
    @PostMapping("/receiveSignResult")
    public Object receiveSignResult(@RequestBody ReceiveSignResultReqDTO request) {
        log.info("接收到支付宝签约结果通知报文: {}", request);
        return paySignService.receiveSignResult(request, SignChannelEnum.ALIPAY.getCode());
    }

    /** 支付宝解约结果通知（预留，当前采用同步确认模式，无需回调）。 */
    @PostMapping("/receiveTerminationResult")
    public Object receiveTerminationResult(@RequestBody ReceiveTerminationResultReqDTO request) {
        log.info("接收到支付宝解约结果通知报文: {}", request);
        return paySignService.receiveTerminationResult(request, SignChannelEnum.ALIPAY.getCode());
    }
}
