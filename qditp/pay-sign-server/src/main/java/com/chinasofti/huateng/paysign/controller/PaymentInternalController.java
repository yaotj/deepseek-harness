package com.chinasofti.huateng.paysign.controller;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.RegisterCompletedPayTxnReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.service.PaymentDomainService;
import com.chinasofti.huateng.paysign.service.RefundDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/payment")
public class PaymentInternalController {

    private static final Logger log = LoggerFactory.getLogger(PaymentInternalController.class);

    private final RefundDomainService refundDomainService;

    private final PaymentDomainService paymentDomainService;

    public PaymentInternalController(RefundDomainService refundDomainService,
                                     PaymentDomainService paymentDomainService) {
        this.refundDomainService = refundDomainService;
        this.paymentDomainService = paymentDomainService;
    }

    @PostMapping("/compensateRefundQuery")
    public CompensateNotifyRespDTO compensateRefundQuery() {
        log.info("收到退款回查补偿请求");
        return refundDomainService.compensateRefundQuery();
    }

    @PostMapping("/compensateRefundSummary")
    public CompensateNotifyRespDTO compensateRefundSummary() {
        log.info("收到退款汇总跨表对账补偿请求");
        return refundDomainService.compensateRefundSummary();
    }


    @PostMapping("/registerCompletedTxn")
    public BaseRespDTO registerCompletedTxn(@RequestBody RegisterCompletedPayTxnReqDTO request) {
        log.info("收到登记已完成支付流水请求, request={}", request);
        return paymentDomainService.registerCompletedTxn(request);
    }


    @GetMapping("/queryRefundResult")
    public BaseRespDTO queryRefundResult(@RequestParam("refundOrderNo") String refundOrderNo) {
        log.info("收到只读退款回查请求, refundOrderNo={}", refundOrderNo);
        return refundDomainService.queryRefundResult(refundOrderNo);
    }


    @PostMapping("/compensatePayStatus")
    public CompensateNotifyRespDTO compensatePayStatus() {
        log.info("收到支付状态主动回查补偿请求");
        return paymentDomainService.compensatePayStatus();
    }


    @PostMapping("/replayCallback")
    public PaySignCallbackResult replayCallback(@RequestParam("merchantOrderNo") String merchantOrderNo) {
        log.warn("收到人工重放支付回调请求, merchantOrderNo={}", merchantOrderNo);
        return paymentDomainService.replayCallback(merchantOrderNo);
    }
}
