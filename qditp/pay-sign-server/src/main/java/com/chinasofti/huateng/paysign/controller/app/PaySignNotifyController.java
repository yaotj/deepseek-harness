package com.chinasofti.huateng.paysign.controller.app;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.service.PaySignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/app")
public class PaySignNotifyController {
    private static final Logger log = LoggerFactory.getLogger(PaySignNotifyController.class);

    private final PaySignService paySignService;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public PaySignNotifyController(PaySignService paySignService) {
        this.paySignService = paySignService;
    }

    @PostMapping("/receiveSignResult")
    public PaySignCallbackResult receiveSignResult(@RequestBody ReceiveSignResultReqDTO request) {
        log.info("接收到签约结果通知报文: {}", request);
        return paySignService.receiveSignResult(request, SignChannelEnum.METRO_APP.getCode());
    }
}
