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

@RestController
@RequestMapping("/notify")
public class PaySignAlipayTripNotifyController {
    private static final Logger log = LoggerFactory.getLogger(PaySignAlipayTripNotifyController.class);

    private final PaySignService paySignService;

    public PaySignAlipayTripNotifyController(PaySignService paySignService) {
        this.paySignService = paySignService;
    }

    @PostMapping("/receiveSignResult")
    public Object receiveSignResult(@RequestBody ReceiveSignResultReqDTO request) {
        log.info("接收到支付宝签约结果通知报文: {}", request);
        return paySignService.receiveSignResult(request, SignChannelEnum.ALIPAY.getCode());
    }

    @PostMapping("/receiveTerminationResult")
    public Object receiveTerminationResult(@RequestBody ReceiveTerminationResultReqDTO request) {
        log.info("接收到支付宝解约结果通知报文: {}", request);
        return paySignService.receiveTerminationResult(request, SignChannelEnum.ALIPAY.getCode());
    }
}
