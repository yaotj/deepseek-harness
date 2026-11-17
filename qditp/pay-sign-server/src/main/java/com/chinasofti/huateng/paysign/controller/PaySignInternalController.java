package com.chinasofti.huateng.paysign.controller;

import com.chinasofti.huateng.model.paysign.ResendSignNotifyReqDTO;
import com.chinasofti.huateng.model.paysign.ResendSignNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.service.SignNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/paySign")
public class PaySignInternalController {

    private static final Logger log = LoggerFactory.getLogger(PaySignInternalController.class);

    private final SignNotifyService signNotifyService;

    public PaySignInternalController(SignNotifyService signNotifyService) {
        this.signNotifyService = signNotifyService;
    }

    @PostMapping("/compensateNotify")
    public CompensateNotifyRespDTO compensateSignNotify() {
        log.info("收到签约流水通知补偿请求");
        return signNotifyService.compensateSignNotify();
    }

    @PostMapping("/resendNotify")
    public ResendSignNotifyRespDTO resendSignNotify(@RequestBody ResendSignNotifyReqDTO request) {
        String requestSignSeq = request == null ? null : request.getRequestSignSeq();
        log.info("收到单条签约结果通知重发请求, requestSignSeq={}", requestSignSeq);
        return signNotifyService.resendSignNotify(requestSignSeq);
    }
}
