package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.PaymentResult;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.api.paycenter.PayCenterResponses;
import com.chinasofti.huateng.facepay.api.paycenter.PayNoticeReqDTO;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterStatus;
import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.domain.F2fOrderRefundStatus;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** TVM / BOM 单程票的支付结果侧：设备查询、支付中心回调、收银台反查订单详情。本类刻意不带 {@code @Transactional}（链路里有支付中心调用），NEVER 加。 */
@Service
public class F2fTvmPayResultService {

    private static final Logger log = LoggerFactory.getLogger(F2fTvmPayResultService.class);

    /** 业务类型：充值。 */
    private static final String BIZ_TOPUP = "02";

    private static final String STATUS_PAY_FAILED = F2fOrderStatus.PAY_FAILED.name();

    /** 对设备口径为「已收款」的内部状态白名单。 */
    private static final List<String> PAID_LIKE = List.of(
            F2fOrderStatus.PAID.name(), F2fOrderStatus.FULFILLED.name(), F2fOrderStatus.FULFILL_FAILED.name(),
            F2fOrderStatus.REFUNDING.name(), F2fOrderStatus.REFUNDED.name());

    /** 对设备口径为「失败」的内部状态白名单。 */
    private static final List<String> FAILED_LIKE = F2fOrderStatus.FAILED_LIKE;

    /** 需要向支付中心查实际结果的状态白名单。 */
    private static final List<String> PENDING = F2fOrderStatus.PENDING;

    /** 收银台侧的时间格式，照搬旧 {@code TvmOrderServiceImpl.DATE_FORMATTER}。 */
    private static final DateTimeFormatter PAY_CENTER_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    /** 只用来取 {@code getPayNoticeUrl()} 回吐给收银台，本类不自己发起外呼。 */
    private final PayCenterClient payCenterClient;

    private final F2fPayCenterFlow payCenterFlow;

    public F2fTvmPayResultService(F2fOrderMapper orderMapper,
                                  F2fPaymentMapper paymentMapper,
                                  PayCenterClient payCenterClient,
                                  F2fPayCenterFlow payCenterFlow) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.payCenterClient = payCenterClient;
        this.payCenterFlow = payCenterFlow;
    }

    /** 查订单。 */
    private F2fOrder loadOrderForQuery(String orderNo) {
        return orderMapper.selectByOrderNo(orderNo);
    }

    /** IF2A-03 查询支付结果。 */
    public JSONObject queryPayResult(RequestPayResultReqDTO request) {
        String orderNo = request.getOrderNo();
        F2fOrder order = loadOrderForQuery(orderNo);
        if (order == null) {
            log.info("查询支付结果 订单不存在, orderNo={}", orderNo);
            return TvmResponses.payResultFail(DeviceRetCode.INVALID_PARAM, "没有找到匹配的订单，请确认订单号是否正确");
        }
        String status = order.getOrderStatus();
        if (PAID_LIKE.contains(status)) {
            return TvmResponses.payResult(PaymentResult.SUCCESS, channelCodeOf(orderNo));
        }
        if (FAILED_LIKE.contains(status)) {
            return TvmResponses.payResult(PaymentResult.FAILED, channelCodeOf(orderNo));
        }
        if (!PENDING.contains(status)) {
            log.warn("查询支付结果 命中未预期状态，按支付中处理, orderNo={}, status={}", orderNo, status);
            return TvmResponses.payResult(PaymentResult.ORDERED, channelCodeOf(orderNo));
        }
        return queryAtPayCenter(orderNo);
    }

    /** 向支付中心查实际结果并收口本地状态。 */
    private JSONObject queryAtPayCenter(String orderNo) {
        F2fPayCenterFlow.Settled settled = payCenterFlow.settle(new F2fPayCenterFlow.SettleSpec(
                orderNo, PENDING,
                result -> markPaymentSuccess(orderNo, result, "查询到支付成功"),
                "TVM"));
        PayCenterResult result = settled.result();
        String vendor = result.string("paymentVendor");
        return switch (settled.settlement()) {
            case PAID -> TvmResponses.payResult(PaymentResult.SUCCESS, vendor);
            case FAILED, UNPAID -> TvmResponses.payResult(PaymentResult.FAILED, vendor);
            case PENDING -> TvmResponses.payResult(PaymentResult.ORDERED,
                    result.isTransportFailed() || !result.isSuccessCode() ? channelCodeOf(orderNo) : vendor);
        };
    }

    /** 支付结果回调（支付中心 → ITP）。 */
    public JSONObject receivePayNotice(PayNoticeReqDTO request) {
        String orderNo = request.getMerchantOrderNo();
        if (orderNo == null || orderNo.isBlank()) {
            log.warn("支付回调缺少 merchantOrderNo, request={}", request);
            return PayCenterResponses.fail("merchantOrderNo不能为空");
        }
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.warn("支付回调订单不存在, merchantOrderNo={}", orderNo);
            return PayCenterResponses.orderNotExist();
        }
        String status = order.getOrderStatus();
        if (PAID_LIKE.contains(status) || FAILED_LIKE.contains(status)) {
            log.info("支付回调重复到达，已是终态直接回成功, orderNo={}, status={}", orderNo, status);
            return PayCenterResponses.success();
        }

        PayCenterStatus notified = PayCenterStatus.fromCode(request.getStatus());
        Integer attemptNo = lastAttemptNo(orderNo);
        LocalDateTime now = LocalDateTime.now();
        if (notified == PayCenterStatus.SUCCESS) {
            LocalDateTime paidTms = parseCallbackPayTime(request.getPayTime(), now);
            paymentMapper.markSuccess(orderNo, attemptNo, request.getOrderNo(), request.getChannelOrderNo(),
                    request.getPaymentVendor(), PayCenterResponses.CODE_SUCCESS, "回调通知支付成功", paidTms);
            paymentMapper.updateCallbackAmounts(orderNo, attemptNo,
                    parseCallbackAmount(request.getCashAmount()),
                    parseCallbackAmount(request.getCouponAmount()));
            int updated = orderMapper.markPaid(orderNo, paidTms);
            log.info("支付回调置为已支付, orderNo={}, paidTms={}, cashAmount={}, couponAmount={}, updatedRows={}",
                    orderNo, paidTms, request.getCashAmount(), request.getCouponAmount(), updated);
            payCenterFlow.enqueuePayResultNotify(orderNo, request.getOrderNo(), paidTms);
            return PayCenterResponses.success();
        }
        if (notified != null && notified.isFailed()) {
            paymentMapper.markFinalStatus(orderNo, attemptNo, List.of("INIT", "PROCESSING"), "FAILED",
                    PayCenterResponses.CODE_SUCCESS, "回调通知支付失败", null, now);
            payCenterFlow.warnIfConflict(orderNo, orderMapper.updateStatus(orderNo, PENDING, STATUS_PAY_FAILED,
                    "回调通知支付失败"), F2fOrderStatus.PAY_FAILED, "回调通知支付失败");
            log.info("支付回调置为支付失败, orderNo={}", orderNo);
            return PayCenterResponses.success();
        }
        log.warn("支付回调状态不明确，回失败让支付中心重推, orderNo={}, status={}", orderNo, request.getStatus());
        return PayCenterResponses.fail();
    }

    /**
     * 契约 §5.1 的 {@code payTime} 是 {@code yyyyMMddHHmmss}，MUST 按它落 PAID_TMS / FINISH_TMS。     *
     * <p>NEVER 退回用本机 {@code now()}：跨零点到达的回调会把交易落到错误账期，而对账按支付时间切窗口。
     * 只有报文缺失或格式不认识时才回落本机时间，并打 WARN。
     */
    private LocalDateTime parseCallbackPayTime(String payTime, LocalDateTime fallback) {
        if (payTime == null || payTime.isBlank()) {
            return fallback;
        }
        try {
            return LocalDateTime.parse(payTime.trim(), PAY_CENTER_DATE_FORMATTER);
        } catch (RuntimeException e) {
            log.warn("支付回调 payTime 格式无法解析，回落本机时间, payTime={}", payTime);
            return fallback;
        }
    }

    /**
     * 回调里的金额是字符串，落库前转成整数（分）。解析不了只返回 null、让 NVL 保住原值，
     * NEVER 因为金额格式不对就拒绝回调 —— 状态收口比这两个统计字段重要。
     */
    private Integer parseCallbackAmount(String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(amount.trim());
        } catch (NumberFormatException e) {
            log.warn("支付回调金额非数字，跳过该字段落库, amount={}", amount);
            return null;
        }
    }

    /** 支付中心反查 ITP 订单详情（收银台页面渲染用）。 */
    public JSONObject requestPayOrderDetail(RequestPayResultReqDTO request) {
        String orderNo = request.getOrderNo();
        F2fOrder order = loadOrderForQuery(orderNo);
        if (order == null) {
            log.info("支付中心查询订单详情 订单不存在, orderNo={}", orderNo);
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM, "没有找到匹配的订单，请确认订单号是否正确");
        }
        if (BIZ_TOPUP.equals(order.getBizType())) {
            return TvmResponses.topupOrderDetail(orderNo, order.getDeviceId(), order.getOrderAmount(),
                    format(order.getCreateTms()), payCenterOrderStatus(order),
                    format(order.getPaidTms()), payCenterClient.properties().getPayNoticeUrl());
        }
        return TvmResponses.payOrderDetail(orderNo, order.getEntryStationCode(), order.getExitStationCode(),
                order.getTicketNum(), order.getOrderAmount(), format(order.getCreateTms()),
                payCenterOrderStatus(order), format(order.getPaidTms()),
                payCenterClient.properties().getPayNoticeUrl());
    }

    /** 内部状态 → 收银台口径。 */
    private static String payCenterOrderStatus(F2fOrder order) {
        String orderStatus = order.getOrderStatus();
        if (PAID_LIKE.contains(orderStatus)) {
            return F2fOrderRefundStatus.refunded(order.getRefundStatus()) ? "7" : "2";
        }
        if (PENDING.contains(orderStatus)) {
            return "1";
        }
        return "";
    }

    /** {@code yyyyMMddHHmmss}，null 进 null 出——{@code payDate} 为 null 时该 key 不下发。 */
    private static String format(LocalDateTime tms) {
        return tms == null ? null : tms.format(PAY_CENTER_DATE_FORMATTER);
    }

    /** 回调要落在最近一次支付尝试上；查不到时按 1 处理（预下单必然已插过 attempt 1）。 */
    private Integer lastAttemptNo(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null || last.getAttemptNo() == null ? 1 : last.getAttemptNo();
    }

    /** 据支付中心查询结果把最近一次支付尝试收口为 SUCCESS，并回填渠道码与两个外部订单号。 */
    public void markPaymentSuccess(String orderNo, PayCenterResult result, String retMsg) {
        try {
            int updated = paymentMapper.markSuccess(orderNo, lastAttemptNo(orderNo),
                    result.string("orderNo"), result.string("channelOrderNo"),
                    result.string("paymentVendor"), PayCenterResponses.CODE_SUCCESS, retMsg,
                    LocalDateTime.now());
            if (updated == 0) {
                log.info("支付尝试已是终态，无需再置成功, orderNo={}", orderNo);
            }
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            log.info("该订单已有成功的支付尝试，幂等跳过, orderNo={}", orderNo);
        }
    }

    /** 取该订单最近一次支付尝试的渠道码，用于回吐 {@code paymentChannelCode}。 */
    private String channelCodeOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayChannelCode();
    }
}
