package com.chinasofti.huateng.paysign.controller.ticket;

import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.service.PaySignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ticket")
public class TerminationNotifyController {
    private static final Logger log = LoggerFactory.getLogger(TerminationNotifyController.class);

    private final PaySignService paySignService;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public TerminationNotifyController(PaySignService paySignService) {
        this.paySignService = paySignService;
    }

    @PostMapping("/receiveTerminationResultFromItp")
    public BaseRespDTO receiveTerminationResult(@RequestBody ReceiveTerminationResultReqDTO request) {
        log.info("接收到解约结果通知报文: {}", request);
        return paySignService.receiveTerminationResult(request, SignChannelEnum.METRO_APP.getCode());
    }
}
