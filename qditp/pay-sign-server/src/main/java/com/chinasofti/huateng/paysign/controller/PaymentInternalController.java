package com.chinasofti.huateng.paysign.controller;

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

/** ITP 支付（含退款）内部接口控制器。 */
@RestController
@RequestMapping("/internal/payment")
public class PaymentInternalController {

    private static final Logger log = LoggerFactory.getLogger(PaymentInternalController.class);

    private final RefundDomainService refundDomainService;

    private final PaymentDomainService paymentDomainService;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public PaymentInternalController(RefundDomainService refundDomainService,
                                     PaymentDomainService paymentDomainService) {
        this.refundDomainService = refundDomainService;
        this.paymentDomainService = paymentDomainService;
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

    /**
     * 登记一条「已完成、不经支付中心」的支付流水（BOM 补站等现场已收款的订单）。
     *
     * <p>调用方是 gate-txn-pay 的落单链路，**它 MUST 在落单之前调本端点、失败即整笔失败**
     * （用户 2026-09-22 选定强一致口径）：顺序颠倒就会留下「订单已 SUCCESS、流水缺行」，
     * 而这正是本次改造要消灭的状态。本端点按 {@code UK_PAY_TXN_DETAIL_ORDER} 幂等，重试安全。
     *
     * <p>与 §5.2「新增状态变更型接口 MUST 有鉴权」的偏差：沿用本模块 {@code /internal/**} 其余 9 个端点的
     * 现状（无鉴权，见 AGENTS.md §2.2.2 对账那条「X-Recon-Token 已整段删除」的同期裁决），
     * **上生产前 MUST 与那批端点一起补齐**，NEVER 在此单独自造一套签名。
     */
    @PostMapping("/registerCompletedTxn")
    public BaseRespDTO registerCompletedTxn(@RequestBody RegisterCompletedPayTxnReqDTO request) {
        log.info("收到登记已完成支付流水请求, request={}", request);
        return paymentDomainService.registerCompletedTxn(request);
    }

    /**
     * 只读回查支付中心 §3.2 退款查询，给「我方记失败 / 悬挂、支付中心却已退」的退款单定性。
     *
     * <p>**只读端点：不改任何一张表**，因此与 §5.2「新增状态变更型接口 MUST 有鉴权」不冲突；
     * 但它会把支付中心原始应答透出，**NEVER 在此基础上加任何写操作**——要推状态走
     * {@code /compensateRefundQuery}。入参就是我方退款流水号 {@code PAY_REFUND_DETAIL.REFUND_ORDER_NO}。</p>
     */
    @GetMapping("/queryRefundResult")
    public BaseRespDTO queryRefundResult(@RequestParam("refundOrderNo") String refundOrderNo) {
        log.info("收到只读退款回查请求, refundOrderNo={}", refundOrderNo);
        return refundDomainService.queryRefundResult(refundOrderNo);
    }
}
