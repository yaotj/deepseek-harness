package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterStatus;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatusTransition;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Service
public class F2fPayCenterFlow {

    private static final Logger log = LoggerFactory.getLogger(F2fPayCenterFlow.class);

    private static final List<String> PAYMENT_INIT = List.of("INIT");

    private static final List<String> PAYABLE_FROM_CREATED = List.of(F2fOrderStatus.CREATED.name());

    private static final String PAYING = F2fOrderStatus.PAYING.name();

    private static final DateTimeFormatter PAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final int NO_USER_ID_NOTIFY_RETRY = 1;

    private final PayCenterClient payCenterClient;

    private final PayCenterMessageFactory messageFactory;

    private final F2fPaymentMapper paymentMapper;

    private final F2fOrderMapper orderMapper;

    private final F2fNotifyService notifyService;

    public F2fPayCenterFlow(PayCenterClient payCenterClient,
                            PayCenterMessageFactory messageFactory,
                            F2fPaymentMapper paymentMapper,
                            F2fOrderMapper orderMapper,
                            F2fNotifyService notifyService) {
        this.payCenterClient = payCenterClient;
        this.messageFactory = messageFactory;
        this.paymentMapper = paymentMapper;
        this.orderMapper = orderMapper;
        this.notifyService = notifyService;
    }
    public record RejectTransition(List<String> fromStatuses, String reasonPrefix) {
    }

    public record SubmitSpec(String orderNo,
                             int attemptNo,
                             PayCenterRequest message,
                             String dataAlias,
                             RejectTransition rejectTransition,
                             boolean inspectSyncStatus,
                             String scene) {

        public boolean dataRequired() {
            return dataAlias != null;
        }
    }

    public sealed interface Submitted {

        record Accepted(PayCenterResult result) implements Submitted {
        }

        record Rejected(PayCenterResult result) implements Submitted {
        }

        record Unknown(PayCenterResult result, String reason) implements Submitted {
        }

        record SyncPaid(PayCenterResult result) implements Submitted {
        }
    }
    public Submitted submit(SubmitSpec spec) {
        String orderNo = spec.orderNo();
        PayCenterResult result = payCenterClient.execute(
                payCenterClient.properties().getPayUrl(), spec.message());

        if (result.isTransportFailed()) {
            markPayment(spec, "UNKNOWN", null, result.getFailureReason(), result);
            log.error("{} 支付中心未明确应答，订单不推进等收口, orderNo={}, reason={}",
                    spec.scene(), orderNo, result.getFailureReason());
            return new Submitted.Unknown(result, result.getFailureReason());
        }
        if (!result.isSuccessCode()) {
            markPayment(spec, "FAILED", result.getCode(), result.getMsg(), result);
            pushPayFailed(spec, result.getCode());
            log.warn("{} 被支付中心拒绝, orderNo={}, code={}, msg={}",
                    spec.scene(), orderNo, result.getCode(), result.getMsg());
            return new Submitted.Rejected(result);
        }
        if (spec.inspectSyncStatus()) {
            PayCenterStatus payStatus = result.status();
            if (payStatus == PayCenterStatus.SUCCESS) {
                return new Submitted.SyncPaid(result);
            }
            if (payStatus != null && payStatus.isFailed()) {
                markPayment(spec, "FAILED", result.getCode(), result.getMsg(), result);
                pushPayFailed(spec, result.getCode());
                log.info("{} 支付中心返回支付失败, orderNo={}", spec.scene(), orderNo);
                return new Submitted.Rejected(result);
            }
        }
        if (spec.dataRequired()) {
            String data = result.string("data");
            if (data == null || data.isBlank()) {
                String reason = "受理成功但未返回" + spec.dataAlias();
                markPayment(spec, "UNKNOWN", result.getCode(), reason, result);
                log.error("{} 支付中心 code=0 但 data 为空，按 UNKNOWN 处理, orderNo={}",
                        spec.scene(), orderNo);
                return new Submitted.Unknown(result, reason);
            }
        }
        markPayment(spec, "PROCESSING", result.getCode(), result.getMsg(), result);
        orderMapper.updateStatus(orderNo, PAYABLE_FROM_CREATED, PAYING, null);         log.info("{} 支付中心已受理, orderNo={}, payCenterOrderNo={}",
                spec.scene(), orderNo, result.string("orderNo"));
        return new Submitted.Accepted(result);
    }

    private void markPayment(SubmitSpec spec, String payStatus, String code, String msg,
                             PayCenterResult result) {
        paymentMapper.markFinalStatus(spec.orderNo(), spec.attemptNo(), PAYMENT_INIT, payStatus,
                code, msg, (int) result.getCostMs(), LocalDateTime.now());
    }

    private void pushPayFailed(SubmitSpec spec, String payCenterCode) {
        RejectTransition transition = spec.rejectTransition();
        if (transition == null) {
            return;
        }
        warnIfConflict(spec.orderNo(),
                orderMapper.updateStatus(spec.orderNo(), transition.fromStatuses(),
                        F2fOrderStatus.PAY_FAILED.name(), transition.reasonPrefix() + payCenterCode),
                F2fOrderStatus.PAY_FAILED, spec.scene());
    }
    public enum Settlement {
        PAID,
        FAILED,
        UNPAID,
        PENDING
    }

    public record Settled(Settlement settlement, PayCenterResult result) {
    }

    public record SettleSpec(String orderNo,
                             List<String> fromStatuses,
                             Consumer<PayCenterResult> onPaid,
                             String scene,
                             ExpireOnUnpaid expireOnUnpaid,
                             BiConsumer<PayCenterResult, LocalDateTime> onPaidNotify) {

        public SettleSpec(String orderNo, List<String> fromStatuses,
                          Consumer<PayCenterResult> onPaid, String scene) {
            this(orderNo, fromStatuses, onPaid, scene, null, null);
        }
    }

    public record ExpireOnUnpaid(String bizErrorReason, String unpaidReason) {
    }

    public Settled settle(SettleSpec spec) {
        String orderNo = spec.orderNo();
        PayCenterResult result = payCenterClient.execute(
                payCenterClient.properties().getQueryUrl(), messageFactory.buildQueryRequest(orderNo));

        if (result.isTransportFailed() || (!result.isSuccessCode() && spec.expireOnUnpaid() == null)) {
            log.warn("{} 查询支付中心未得到有效结果，按处理中返回, orderNo={}, transportFailed={}, code={}, reason={}",
                    spec.scene(), orderNo, result.isTransportFailed(), result.getCode(), result.getFailureReason());
            return new Settled(Settlement.PENDING, result);
        }
        if (!result.isSuccessCode()) {
            pushExpired(spec, spec.expireOnUnpaid().bizErrorReason(), "支付中心无此订单");
            log.warn("{} 支付中心明确无此订单，置 EXPIRED, orderNo={}, code={}, msg={}",
                    spec.scene(), orderNo, result.getCode(), result.getMsg());
            return new Settled(Settlement.UNPAID, result);
        }
        PayCenterStatus payStatus = result.status();
        if (payStatus == PayCenterStatus.SUCCESS) {
            spec.onPaid().accept(result);
            if (spec.onPaidNotify() == null) {
                markPaidAndReport(orderNo);
                log.info("{} 查询到支付成功, orderNo={}", spec.scene(), orderNo);
            } else {
                spec.onPaidNotify().accept(result, LocalDateTime.now());
            }
            return new Settled(Settlement.PAID, result);
        }
        boolean failed = payStatus != null && payStatus.isFailed();
        if (failed && spec.expireOnUnpaid() == null) {
            warnIfConflict(orderNo, orderMapper.updateStatus(orderNo, spec.fromStatuses(),
                            F2fOrderStatus.PAY_FAILED.name(), "支付中心返回支付失败"),
                    F2fOrderStatus.PAY_FAILED, spec.scene() + " 查询到支付失败");
            log.info("{} 查询到支付失败, orderNo={}", spec.scene(), orderNo);
            return new Settled(Settlement.FAILED, result);
        }
        if (failed || payStatus == PayCenterStatus.UNPAID) {
            pushExpired(spec, spec.expireOnUnpaid() == null
                    ? "支付中心返回未支付" : spec.expireOnUnpaid().unpaidReason(), "查询到未支付");
            log.info("{} 查询到未支付，置 EXPIRED, orderNo={}, payCenterStatus={}",
                    spec.scene(), orderNo, payStatus);
            return new Settled(Settlement.UNPAID, result);
        }
        return new Settled(Settlement.PENDING, result);
    }

    private void pushExpired(SettleSpec spec, String reason, String sceneSuffix) {
        warnIfConflict(spec.orderNo(), orderMapper.updateStatus(spec.orderNo(), spec.fromStatuses(),
                        F2fOrderStatus.EXPIRED.name(), reason),
                F2fOrderStatus.EXPIRED, spec.scene() + " " + sceneSuffix);
    }

    public void markPaidAndReport(String orderNo) {
        markPaidAndReport(orderNo, null);
    }

    public void markPaidAndReport(String orderNo, String tradeNo) {
        LocalDateTime now = LocalDateTime.now();
        F2fOrderStatusTransition.Result transit = F2fOrderStatusTransition.classify(
                orderMapper.markPaid(orderNo, now),
                F2fOrderStatus.PAID, () -> orderMapper.selectOrderStatus(orderNo));
        if (transit.conflict()) {
            log.error("F2F CAS 冲突 已收款但订单状态未推进到 PAID，需人工核对是否应退款, orderNo={}, observed={}",
                    orderNo, transit.observedStatus());
        }
        enqueuePayResultNotify(orderNo, tradeNo, now);
    }

    public void enqueuePayResultNotify(String orderNo, String tradeNo, LocalDateTime paidTms) {
        try {
            F2fOrder order = orderMapper.selectByOrderNo(orderNo);
            if (order == null) {
                log.error("IF8B-05 支付结果通知入队失败：订单不存在, orderNo={}", orderNo);
                return;
            }
            if (!F2fChannel.APP.equals(order.getChannel())) {
                log.info("IF8B-05 跳过非 APP 渠道订单，不入队, orderNo={}, channel={}, bizType={}",
                        orderNo, order.getChannel(), order.getBizType());
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", order.getThirdUserId());
            payload.put("orderNo", order.getOrderNo());
            payload.put("tradeNo", tradeNo == null ? "" : tradeNo);
            payload.put("payResult", "SUCCESS");
            payload.put("payAmount", order.getOrderAmount() == null ? "" : String.valueOf(order.getOrderAmount()));
            payload.put("payDate", paidTms == null ? "" : PAY_DATE_FORMATTER.format(paidTms));
            payload.put("voucher", "");
            payload.put("orderType", "0");

            boolean hasUserId = order.getThirdUserId() != null && !order.getThirdUserId().isBlank();
            boolean enqueued = hasUserId
                    ? notifyService.enqueue(F2fNotifyService.TYPE_PAY_RESULT, orderNo, null, payload)
                    : notifyService.enqueue(F2fNotifyService.TYPE_PAY_RESULT, orderNo, null, payload,
                            NO_USER_ID_NOTIFY_RETRY);
            log.info("IF8B-05 支付结果通知入队完成, orderNo={}, tradeNo={}, enqueued={}, hasUserId={}",
                    orderNo, tradeNo, enqueued, hasUserId);
        } catch (RuntimeException e) {
            log.error("IF8B-05 支付结果通知入队异常，已吞掉不影响支付收口, orderNo={}", orderNo, e);
        }
    }

    public void warnIfConflict(String orderNo, int updatedRows, F2fOrderStatus target, String scene) {
        F2fOrderStatusTransition.Result transit = F2fOrderStatusTransition.classify(
                updatedRows, target, () -> orderMapper.selectOrderStatus(orderNo));
        if (transit.conflict()) {
            log.warn("F2F CAS 冲突 {} 未推进到 {}，仍按原口径应答, orderNo={}, observed={}",
                    scene, target, orderNo, transit.observedStatus());
        }
    }
}
