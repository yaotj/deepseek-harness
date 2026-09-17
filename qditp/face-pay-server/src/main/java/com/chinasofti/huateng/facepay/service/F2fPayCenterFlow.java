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

/** 「与支付中心交互并收口本地状态」这条骨架的唯一实现（模板方法 + 结果策略）。本类刻意不带 {@code @Transactional}（链路里有支付中心调用），NEVER 加。 */
@Service
public class F2fPayCenterFlow {

    private static final Logger log = LoggerFactory.getLogger(F2fPayCenterFlow.class);

    /** 支付流水的初始态，{@code markFinalStatus} 的 CAS 前置。 */
    private static final List<String> PAYMENT_INIT = List.of("INIT");

    /** 受理成功后推 {@code PAYING} 的 CAS 前置，只认 {@code CREATED}。 */
    private static final List<String> PAYABLE_FROM_CREATED = List.of(F2fOrderStatus.CREATED.name());

    /** 「已发起支付」的记账态。 */
    private static final String PAYING = F2fOrderStatus.PAYING.name();

    /** IF8B-05 {@code payDate} 的格式。 */
    private static final DateTimeFormatter PAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 设备单（{@code THIRD_USER_ID} 为空）推 IF8B-05 时的重试上限：一次即终态。 */
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
    /**
     * 被支付中心拒绝时的订单侧推进策略。
     *
     * @param fromStatuses CAS 的前置状态白名单
     * @param reasonPrefix 落到 {@code ORDER_STATUS} 变更原因里的前缀，后面拼支付中心 code
     */
    public record RejectTransition(List<String> fromStatuses, String reasonPrefix) {
    }

    /** 预下单/扣款的入参。 */
    public record SubmitSpec(String orderNo,
                             int attemptNo,
                             PayCenterRequest message,
                             String dataAlias,
                             RejectTransition rejectTransition,
                             boolean inspectSyncStatus,
                             String scene) {

        /** {@code dataAlias} 非空即要求应答里的 {@code data} 有值（二维码串 / 支付信息）。 */
        public boolean dataRequired() {
            return dataAlias != null;
        }
    }

    /** 预下单/扣款的判定结果，调用方 MUST 穷尽分支。 */
    public sealed interface Submitted {

        /** 已受理：支付流水置 {@code PROCESSING}、订单已尝试推 {@code PAYING}。 */
        record Accepted(PayCenterResult result) implements Submitted {
        }

        /** 明确被拒：支付流水置 {@code FAILED}，订单按 {@link RejectTransition} 处理。 */
        record Rejected(PayCenterResult result) implements Submitted {
        }

        /** 状态不明：支付流水置 {@code UNKNOWN}，订单一律不动，留给收口任务。 */
        record Unknown(PayCenterResult result, String reason) implements Submitted {
        }

        /** 同步已收款（只有付款码会走到）。 */
        record SyncPaid(PayCenterResult result) implements Submitted {
        }
    }
    /** 预下单 / 扣款：发往支付中心，按应答收口支付流水与订单状态。 */
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
        orderMapper.updateStatus(orderNo, PAYABLE_FROM_CREATED, PAYING, null); // CAS-DISCARD: PAYING 是记账标记，写不上不改变任何后续判断
        log.info("{} 支付中心已受理, orderNo={}, payCenterOrderNo={}",
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
    /** 查询收口的判定结果。 */
    public enum Settlement {
        /** 支付中心明确已收款，本地已尝试推 {@code PAID}。 */
        PAID,
        /** 支付中心明确支付失败，本地已尝试推 {@code PAY_FAILED}。 */
        FAILED,
        /** 支付中心明确未支付，本地已尝试推 {@code EXPIRED}。 */
        UNPAID,
        /** 没问出结论（传输失败 / 业务码非 0 / 状态仍在处理中）。 */
        PENDING
    }

    /**
     * @param result 原始应答，供调用方取 {@code paymentVendor} / {@code channelOrderNo} 等渠道字段
     */
    public record Settled(Settlement settlement, PayCenterResult result) {
    }

    /**
     * 查询收口的入参。
     *
     * @param onPaid 查到已收款时先执行的支付流水回写（各渠道列不同，故为策略回调）；
     * @param expireOnUnpaid 「问不到就当没付过」策略，null 表示不启用（设备 / APP 查询链路的默认口径）。
     * @param onPaidNotify 收款后的自定义收口，null 表示走本类的 {@link #markPaidAndReport(String)}。
     */
    public record SettleSpec(String orderNo,
                             List<String> fromStatuses,
                             Consumer<PayCenterResult> onPaid,
                             String scene,
                             ExpireOnUnpaid expireOnUnpaid,
                             BiConsumer<PayCenterResult, LocalDateTime> onPaidNotify) {

        /** 设备 / APP 查询链路用的短构造：不启用过期判定、收款后走 {@code markPaidAndReport}。 */
        public SettleSpec(String orderNo, List<String> fromStatuses,
                          Consumer<PayCenterResult> onPaid, String scene) {
            this(orderNo, fromStatuses, onPaid, scene, null, null);
        }
    }

    /**
     * 过期收口把「未支付」落库时用的两句变更原因。
     *
     * @param bizErrorReason 对端答上来但业务码非 0（实测 9999「未找到数据」）时写入的原因
     * @param unpaidReason   对端明确报未支付 / 支付失败时写入的原因
     */
    public record ExpireOnUnpaid(String bizErrorReason, String unpaidReason) {
    }

    /** 向支付中心查实际结果并收口本地状态。 */
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

    /** 推 {@code PAID} 并记「已收款但订单不在可支付状态」。 */
    public void markPaidAndReport(String orderNo) {
        markPaidAndReport(orderNo, null);
    }

    /**
     * 同上，并额外把支付中心订单号带进 IF8B-05 通知。
     *
     * @param tradeNo 支付中心订单号，拿不到时传 null（通知里上送空串）
     */
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

    /**
     * IF8B-05 支付结果通知（我方 → APP_SERVER）的生产者：只入队，不投递。
     *
     * @param tradeNo 支付中心订单号；null 时上送空串
     * @param paidTms 支付成功时刻，MUST 由调用方传入刚刚写进 {@code PAID_TMS} 的那个值。
     */
    public void enqueuePayResultNotify(String orderNo, String tradeNo, LocalDateTime paidTms) {
        try {
            F2fOrder order = orderMapper.selectByOrderNo(orderNo);
            if (order == null) {
                log.error("IF8B-05 支付结果通知入队失败：订单不存在, orderNo={}", orderNo);
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

    /** 记「置失败态的 CAS 没命中」。 */
    public void warnIfConflict(String orderNo, int updatedRows, F2fOrderStatus target, String scene) {
        F2fOrderStatusTransition.Result transit = F2fOrderStatusTransition.classify(
                updatedRows, target, () -> orderMapper.selectOrderStatus(orderNo));
        if (transit.conflict()) {
            log.warn("F2F CAS 冲突 {} 未推进到 {}，仍按原口径应答, orderNo={}, observed={}",
                    scene, target, orderNo, transit.observedStatus());
        }
    }
}
