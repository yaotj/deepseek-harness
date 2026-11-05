package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.PaymentResult;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestPaymentReqDTO;
import com.chinasofti.huateng.facepay.api.paycenter.PayCenterResponses;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterPayCommand;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayScene;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class F2fScanPayService {

    private static final Logger log = LoggerFactory.getLogger(F2fScanPayService.class);

    private static final String STATUS_CREATED = F2fOrderStatus.CREATED.name();
    private static final String STATUS_PAYING = F2fOrderStatus.PAYING.name();
    private static final String STATUS_PAY_FAILED = F2fOrderStatus.PAY_FAILED.name();

    private static final List<String> PAID_LIKE = List.of(
            F2fOrderStatus.PAID.name(), F2fOrderStatus.FULFILLED.name(), F2fOrderStatus.FULFILL_FAILED.name(),
            F2fOrderStatus.REFUNDING.name(), F2fOrderStatus.REFUNDED.name());

    private static final List<String> FAILED_LIKE = F2fOrderStatus.FAILED_LIKE;

    private static final List<String> PAYABLE = F2fOrderStatus.PENDING;

    private static final String PAY_SUBJECT = "bom支付";

    private static final String PAY_BODY = "地铁单程票";

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final PayCenterMessageFactory messageFactory;

    private final F2fPayCenterFlow payCenterFlow;

    public F2fScanPayService(F2fOrderMapper orderMapper,
                             F2fPaymentMapper paymentMapper,
                             PayCenterMessageFactory messageFactory,
                             F2fPayCenterFlow payCenterFlow) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.messageFactory = messageFactory;
        this.payCenterFlow = payCenterFlow;
    }

    public JSONObject requestPayment(RequestPaymentReqDTO request) {
        String orderNo = request.getOrderNo();
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("付款码支付 订单不存在, orderNo={}", orderNo);
            return BomResponses.paymentResultFail(BomResponses.CODE_ORDER_NO_ERROR,
                    "订单号错误,没有找到匹配的订单");
        }
        String status = order.getOrderStatus();
        if (PAID_LIKE.contains(status)) {
            log.info("付款码支付 订单已收款，幂等返回成功, orderNo={}, status={}", orderNo, status);
            return BomResponses.paymentResult(PaymentResult.SUCCESS, "支付成功");
        }
        if (FAILED_LIKE.contains(status)) {
            log.info("付款码支付 订单已是失败终态, orderNo={}, status={}", orderNo, status);
            return BomResponses.paymentResult(PaymentResult.FAILED, "支付失败");
        }
        if (!PAYABLE.contains(status)) {
            log.warn("付款码支付 命中未预期状态，拒绝扣款, orderNo={}, status={}", orderNo, status);
            return BomResponses.paymentResultFail(BomResponses.CODE_FAIL, "订单状态不允许支付");
        }
        if (order.getOrderAmount() == null || order.getOrderAmount() <= 0) {
            log.error("付款码支付 订单金额非法, orderNo={}, amount={}", orderNo, order.getOrderAmount());
            return BomResponses.paymentResultFail(BomResponses.CODE_FAIL, "订单金额异常，请联系工作人员");
        }
        return payAtPayCenter(order, request);
    }

    private JSONObject payAtPayCenter(F2fOrder order, RequestPaymentReqDTO request) {
        String orderNo = order.getOrderNo();
        long amount = order.getOrderAmount();
        LocalDateTime now = LocalDateTime.now();

        PayCenterRequest message;
        try {
            message = messageFactory.buildPayRequest(new PayCenterPayCommand(
                    orderNo, PayScene.SCAN, request.getPaymentCode(), null, amount,
                    PAY_SUBJECT, PAY_BODY, request.getPaymentVendor()));
        } catch (IllegalArgumentException e) {
            log.warn("付款码支付 报文要素不全，按支付失败返回不发往支付中心, orderNo={}, reason={}",
                    orderNo, e.getMessage());
            return BomResponses.paymentResult(PaymentResult.FAILED, "支付失败");
        }
        int attemptNo = nextAttemptNo(orderNo);
        paymentMapper.insert(buildPayment(order, request, attemptNo, message.getBizData(), now));

        F2fPayCenterFlow.Submitted submitted = payCenterFlow.submit(new F2fPayCenterFlow.SubmitSpec(
                orderNo, attemptNo, message, null,
                new F2fPayCenterFlow.RejectTransition(PAYABLE, "付款码支付被拒:"),
                true, "付款码支付"));
        return switch (submitted) {
            case F2fPayCenterFlow.Submitted.SyncPaid paid -> {
                markSuccess(orderNo, attemptNo, paid.result());
                payCenterFlow.markPaidAndReport(orderNo);
                log.info("付款码支付成功, orderNo={}", orderNo);
                yield BomResponses.paymentResult(PaymentResult.SUCCESS, "支付成功");
            }
            case F2fPayCenterFlow.Submitted.Rejected ignored ->
                    BomResponses.paymentResult(PaymentResult.FAILED, "支付失败");
            case F2fPayCenterFlow.Submitted.Unknown ignored ->
                    BomResponses.paymentResult(PaymentResult.ORDERED, "支付中心未返回结果，请稍后查询");
            case F2fPayCenterFlow.Submitted.Accepted ignored ->
                    BomResponses.paymentResult(PaymentResult.ORDERED, "支付处理中，请稍后查询");
        };
    }

    public JSONObject queryPayResult(String orderNo) {
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("BOM 查询支付结果 订单不存在, orderNo={}", orderNo);
            return BomResponses.paymentResultFail(BomResponses.CODE_ORDER_NO_ERROR,
                    "订单号错误,没有找到匹配的订单");
        }
        String status = order.getOrderStatus();
        if (PAID_LIKE.contains(status)) {
            return BomResponses.paymentResult(PaymentResult.SUCCESS, "支付成功");
        }
        if (FAILED_LIKE.contains(status)) {
            return BomResponses.paymentResult(PaymentResult.FAILED, "支付失败");
        }
        if (!PAYABLE.contains(status)) {
            log.warn("BOM 查询支付结果 命中未预期状态，按处理中返回, orderNo={}, status={}", orderNo, status);
            return BomResponses.paymentResult(PaymentResult.PROCESSING, "处理中");
        }
        return resolve(orderNo);
    }

    private JSONObject resolve(String orderNo) {
        F2fPayCenterFlow.Settled settled = payCenterFlow.settle(new F2fPayCenterFlow.SettleSpec(
                orderNo, PAYABLE,
                result -> markSuccess(orderNo, lastAttemptNo(orderNo), result),
                "BOM 付款码"));
        return switch (settled.settlement()) {
            case PAID -> BomResponses.paymentResult(PaymentResult.SUCCESS, "支付成功");
            case FAILED -> BomResponses.paymentResult(PaymentResult.FAILED, "支付失败");
            case UNPAID -> BomResponses.paymentResult(PaymentResult.FAILED, "未支付");
            case PENDING -> BomResponses.paymentResult(PaymentResult.PROCESSING, "处理中");
        };
    }

    private int lastAttemptNo(String orderNo) {
        Integer max = paymentMapper.selectMaxAttemptNo(orderNo);
        return max == null ? 1 : max;
    }

    private void markSuccess(String orderNo, int attemptNo, PayCenterResult result) {
        try {
            int updated = paymentMapper.markSuccess(orderNo, attemptNo,
                    result.string("orderNo"), result.string("channelOrderNo"),
                    result.string("paymentVendor"), PayCenterResponses.CODE_SUCCESS,
                    "付款码支付成功", LocalDateTime.now());
            if (updated == 0) {
                log.info("付款码支付 该尝试已是终态，无需再置成功, orderNo={}, attemptNo={}", orderNo, attemptNo);
            }
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            log.info("该订单已有成功的支付尝试，幂等跳过, orderNo={}", orderNo);
        }
    }

    private int nextAttemptNo(String orderNo) {
        Integer max = paymentMapper.selectMaxAttemptNo(orderNo);
        return max == null ? 1 : max + 1;
    }

    private F2fPayment buildPayment(F2fOrder order, RequestPaymentReqDTO request, int attemptNo,
                                    String requestBody, LocalDateTime now) {
        F2fPayment payment = new F2fPayment();
        payment.setOrderNo(order.getOrderNo());
        payment.setAttemptNo(attemptNo);
        payment.setPayScene(PayScene.SCAN.getCode());
        payment.setPayStatus("INIT");
        payment.setPayAmount(order.getOrderAmount());
        payment.setPaymentVendor(request.getPaymentCode());
        payment.setAuthCode(request.getPaymentVendor());
        payment.setRequestBody(requestBody);
        payment.setRequestTms(now);
        payment.setCreateTms(now);
        payment.setUpdateTms(now);
        return payment;
    }
}
