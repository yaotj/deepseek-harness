package com.chinasofti.huateng.gatetxnpay.paysign;

import com.chinasofti.huateng.gatetxnpay.constant.DebitStatus;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.notify.GateTxnPayNoticeService;
import com.chinasofti.huateng.gatetxnpay.writer.GateTxnPayWriter;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.app.GatePayRequestDTO;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.paysign.RegisterCompletedPayTxnReqDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


@Component
public class PaySignInitiator {
    private static final Logger log = LoggerFactory.getLogger(PaySignInitiator.class);

    private static final int REMARK_MAX_LENGTH = 512;

    private static final String CHANNEL_PAY_SIGN = "pay-sign";
    private static final String CHANNEL_ALIPAY = "alipay-pay-sign";

    private final GateTxnPayWriter gateTxnPayWriter;
    private final PayInitiationRpcAdapter payInitiationRpcAdapter;
    private final GatePayRequestFactory gatePayRequestFactory;
    private final AlipayTripPayRequestFactory alipayTripPayRequestFactory;
    private final GateTxnPayNoticeService gateTxnPayNoticeService;

    public PaySignInitiator(
            GateTxnPayWriter gateTxnPayWriter,
            PayInitiationRpcAdapter payInitiationRpcAdapter,
            GatePayRequestFactory gatePayRequestFactory,
            AlipayTripPayRequestFactory alipayTripPayRequestFactory,
            GateTxnPayNoticeService gateTxnPayNoticeService) {
        this.gateTxnPayWriter = gateTxnPayWriter;
        this.payInitiationRpcAdapter = payInitiationRpcAdapter;
        this.gatePayRequestFactory = gatePayRequestFactory;
        this.alipayTripPayRequestFactory = alipayTripPayRequestFactory;
        this.gateTxnPayNoticeService = gateTxnPayNoticeService;
    }

    public void initiateAsync(GateTxnPay order, GateTxnPayReqDTO request) {
        try {
            initiateAndConverge(order, request);
        } catch (Exception e) {
            log.error("异步扣费编排异常, orderNo={}, cardId={}", order.getOrderNo(), order.getCardId(), e);
            try {
                gateTxnPayWriter.updateOrderStatusFromPending(order, DebitStatus.RETRY.code(),
                        remark("异步扣费编排异常: " + e.getMessage()));
            } catch (RuntimeException ex) {
                log.error("异步更新订单状态失败, orderNo={}", order.getOrderNo(), ex);
                throw ex;
            }
        }
    }

    public String initiateAndConverge(GateTxnPay order, GateTxnPayReqDTO request) {
        return converge(order, request, "扣费", AlipayTripPayRequestFactory.PAY_TRIGGER_FIRST);
    }


    public String retryAndConverge(GateTxnPay order) {
        return retryAndConverge(order, AlipayTripPayRequestFactory.PAY_TRIGGER_COMPENSATION);
    }

    public String retryAndConverge(GateTxnPay order, String payTriggerType) {
        return converge(order, null, "重试扣费", payTriggerType);
    }


    public String registerCompletedTxnForBomSupplement(GateTxnPay order, GateTxnPayReqDTO request, String reason) {
        RegisterCompletedPayTxnReqDTO payTxnRequest = new RegisterCompletedPayTxnReqDTO();
        payTxnRequest.setOrderNo(order.getOrderNo());
        payTxnRequest.setTxnDate(order.getTxnDate());
        payTxnRequest.setThirdUserId(order.getThirdUserId());
        payTxnRequest.setCardId(order.getCardId());
        payTxnRequest.setCardType(order.getCardType());
        payTxnRequest.setPaymentVendor(order.getPaymentVendor());
        payTxnRequest.setPayUserId(order.getPayUserId());
        payTxnRequest.setAmount(0);
        payTxnRequest.setReason(reason);
        if (request != null) {
            if (StringUtils.hasText(request.getPaymentVendor())) {
                payTxnRequest.setPaymentVendor(request.getPaymentVendor());
            }
            if (StringUtils.hasText(request.getPayUserId())) {
                payTxnRequest.setPayUserId(request.getPayUserId());
            }
            payTxnRequest.setRequestSignSeq(request.getRequestSignSeq());
        }
        RpcOutcome outcome = payInitiationRpcAdapter.registerCompletedTxn(payTxnRequest);
        return switch (outcome) {
            case RpcOutcome.Ok ignored -> {
                log.info("BOM补站单已在支付域登记0元支付流水, orderNo={}, cardId={}, reason={}",
                        order.getOrderNo(), order.getCardId(), reason);
                yield null;
            }
            case RpcOutcome.BizRejected rejected -> {
                log.error("BOM补站单登记支付流水被支付域业务拒绝，本笔不落单, orderNo={}, cardId={}, retCode={}, retMsg={}",
                        order.getOrderNo(), order.getCardId(), rejected.retCode(), rejected.retMsg());
                yield "登记支付流水被拒绝: " + rejected.retCode() + "/" + rejected.retMsg();
            }
            case RpcOutcome.Unreachable unreachable -> {
                log.error("BOM补站单登记支付流水未获支付域答复，本笔不落单等闸机重推, orderNo={}, cardId={}",
                        order.getOrderNo(), order.getCardId(), unreachable.cause());
                yield "登记支付流水未获答复: " + causeOf(unreachable);
            }
        };
    }

    private String converge(GateTxnPay order, GateTxnPayReqDTO request, String scene, String payTriggerType) {
        boolean alipayChannel = IssueChannelCodeEnum.isAlipay(order.getIssueChannelCode());
        String channel = alipayChannel ? CHANNEL_ALIPAY : CHANNEL_PAY_SIGN;
        RpcOutcome outcome = alipayChannel ? requestAlipayTripPay(order, payTriggerType) : requestPaySign(order, request, payTriggerType);
        Landing landing = land(order, scene, channel, outcome);
        int updated = gateTxnPayWriter.updateOrderStatusFromPending(order, landing.status(), landing.reason());
        if (updated > 0 && DebitStatus.FAIL.is(landing.status())) {
            gateTxnPayNoticeService.notifySettled(order, landing.status(), scene + "被" + channel + "业务拒绝");
        }
        return landing.status();
    }


    private Landing land(GateTxnPay order, String scene, String channel, RpcOutcome outcome) {
        return switch (outcome) {
            case RpcOutcome.Ok ignored -> {
                log.info("{}已被{}受理，置 PROCESSING 等支付结果回调, orderNo={}, cardId={}, amount={}",
                        scene, channel, order.getOrderNo(), order.getCardId(), order.getTotalAmount());
                yield new Landing(DebitStatus.PROCESSING.code(), remark(scene + "已被" + channel + "受理"));
            }
            case RpcOutcome.BizRejected rejected -> {
                log.error("{}被{}业务拒绝，已置 FAIL 终态待人工核对（NEVER 重推）, orderNo={}, cardId={}, retCode={}, retMsg={}",
                        scene, channel, order.getOrderNo(), order.getCardId(), rejected.retCode(), rejected.retMsg());
                yield new Landing(DebitStatus.FAIL.code(),
                        remark(scene + "被" + channel + "业务拒绝: " + rejected.retCode() + "/" + rejected.retMsg()));
            }
            case RpcOutcome.Unreachable unreachable -> {
                log.error("{}未获{}业务答复，已置 RETRY 等补偿重试, orderNo={}, cardId={}",
                        scene, channel, order.getOrderNo(), order.getCardId(), unreachable.cause());
                yield new Landing(DebitStatus.RETRY.code(),
                        remark(scene + "未获" + channel + "答复: " + causeOf(unreachable)));
            }
        };
    }

    private RpcOutcome requestPaySign(GateTxnPay order, GateTxnPayReqDTO request, String payTriggerType) {
        GatePayRequestDTO payRequest = gatePayRequestFactory.build(order, request, payTriggerType);
        log.info("调用pay-sign请求支付, 入参 orderNo={}, cardId={}, amount={}, paymentVendor={}, requestSignSeq={}, txnDate={}, discountFee={}, discountInfo={}, payTriggerType={}, industryDetail={}",
                order.getOrderNo(), order.getCardId(), order.getTotalAmount(),
                payRequest.getPaymentVendor(), payRequest.getRequestSignSeq(),
                payRequest.getTxnDate(), payRequest.getDiscountFee(), payRequest.getDiscountInfo(),
                payTriggerType, payRequest.getIndustryDetail());
        return payInitiationRpcAdapter.requestPaySign(payRequest);
    }

    private RpcOutcome requestAlipayTripPay(GateTxnPay order, String payTriggerType) {
        AlipayTripRequestPayReqDTO payRequest = alipayTripPayRequestFactory.build(order, payTriggerType);
        log.info("调用alipay-pay-sign请求支付, 入参 orderNo={}, cardId={}, amount={}, requestSignSeq={}, txnDate={}, payTriggerType={}, industryDetail={}",
                order.getOrderNo(), order.getCardId(), order.getTotalAmount(),
                payRequest.getRequestSignSeq(), order.getTxnDate(), payTriggerType, payRequest.getIndustryDetail());
        return payInitiationRpcAdapter.requestAlipayTripPay(payRequest);
    }

    private String causeOf(RpcOutcome.Unreachable unreachable) {
        Throwable cause = unreachable.cause();
        if (cause == null) {
            return "未知原因";
        }
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }

    private String remark(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > REMARK_MAX_LENGTH ? text.substring(0, REMARK_MAX_LENGTH) : text;
    }

    private record Landing(String status, String reason) {
    }
}
