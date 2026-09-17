package com.chinasofti.huateng.paysign.controller;

import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.service.RefundDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ITP 支付（含退款）内部接口控制器。 */
@RestController
@RequestMapping("/internal/payment")
public class PaymentInternalController {

    private static final Logger log = LoggerFactory.getLogger(PaymentInternalController.class);

    private final RefundDomainService refundDomainService;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public PaymentInternalController(RefundDomainService refundDomainService) {
        this.refundDomainService = refundDomainService;
    }

    /** 退款回查补偿：无入参，内部扫一批停在 {@code PROCESSING} 的 {@code PAY_REFUND_DETAIL}。 */
    @PostMapping("/compensateRefundQuery")
    public CompensateNotifyRespDTO compensateRefundQuery() {
        log.info("收到退款回查补偿请求");
        return refundDomainService.compensateRefundQuery();
    }

    /** 退款汇总跨表对账补偿：无入参，内部扫一批 {@code PAY_REFUND_DETAIL}（唯一账本）与。 */
    @PostMapping("/compensateRefundSummary")
    public CompensateNotifyRespDTO compensateRefundSummary() {
        log.info("收到退款汇总跨表对账补偿请求");
        return refundDomainService.compensateRefundSummary();
    }
}
