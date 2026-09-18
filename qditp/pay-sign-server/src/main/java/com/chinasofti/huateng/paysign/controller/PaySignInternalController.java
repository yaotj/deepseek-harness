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

/** ITP 签约内部接口控制器。 */
@RestController
@RequestMapping("/internal/paySign")
public class PaySignInternalController {

    private static final Logger log = LoggerFactory.getLogger(PaySignInternalController.class);

    private final SignNotifyService signNotifyService;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public PaySignInternalController(SignNotifyService signNotifyService) {
        this.signNotifyService = signNotifyService;
    }

    /** 签约结果通知补偿：无入参，内部扫一批 APP_PAY_SIGN_REQUEST 里 OPERATION_TYPE=RECEIVE_SIGN_RESULT。 */
    @PostMapping("/compensateNotify")
    public CompensateNotifyRespDTO compensateSignNotify() {
        log.info("收到签约流水通知补偿请求");
        return signNotifyService.compensateSignNotify();
    }

    /** 单条签约结果通知重发：只处理入参给定的 requestSignSeq，**不扫表、不递增 NOTIFY_RETRY_COUNT**。 */
    @PostMapping("/resendNotify")
    public ResendSignNotifyRespDTO resendSignNotify(@RequestBody ResendSignNotifyReqDTO request) {
        String requestSignSeq = request == null ? null : request.getRequestSignSeq();
        log.info("收到单条签约结果通知重发请求, requestSignSeq={}", requestSignSeq);
        return signNotifyService.resendSignNotify(requestSignSeq);
    }
}
