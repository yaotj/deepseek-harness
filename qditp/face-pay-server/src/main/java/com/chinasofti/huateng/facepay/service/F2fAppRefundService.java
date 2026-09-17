package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.app.AppRefundNotiResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.app.AppResponses;
import com.chinasofti.huateng.facepay.api.device.app.RequestAppPayResultReqDTO;
import com.chinasofti.huateng.facepay.channel.app.AppNotifyProperties;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.entity.F2fRefund;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.mapper.F2fRefundMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** APP 侧退款：申请退款、退款结果查询、支付中心退款结果回调。本类刻意不带 {@code @Transactional}（链路里有支付中心调用），NEVER 加。 */
@Service
public class F2fAppRefundService {

    private static final Logger log = LoggerFactory.getLogger(F2fAppRefundService.class);

    /** 允许发起退款的状态白名单。 */
    private static final List<String> REFUNDABLE = F2fOrderStatus.REFUNDABLE;

    /** 退款相关响应里的 {@code refundDate} 格式：8 位日期。 */
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fRefundMapper refundMapper;

    private final F2fRefundService refundService;

    private final F2fNotifyService notifyService;

    private final String refundNotifyUrl;

    public F2fAppRefundService(F2fOrderMapper orderMapper,
                               F2fPaymentMapper paymentMapper,
                               F2fRefundMapper refundMapper,
                               F2fRefundService refundService,
                               F2fNotifyService notifyService,
                               AppNotifyProperties appNotifyProperties) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.refundMapper = refundMapper;
        this.refundService = refundService;
        this.notifyService = notifyService;
        this.refundNotifyUrl = appNotifyProperties.getRefundNoticeUrl();
    }
    /** 请求退款（整单）。 */
    public JSONObject requestRefund(RequestAppPayResultReqDTO request) {
        String orderNo = request.getOrderNo();
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("APP 请求退款 订单不存在, orderNo={}", orderNo);
            return AppResponses.failMessage("该订单无支付记录，不可退款");
        }
        if (!REFUNDABLE.contains(order.getOrderStatus())) {
            log.info("APP 请求退款 订单状态不允许退款，按旧形态回退款失败, orderNo={}, status={}",
                    orderNo, order.getOrderStatus());
            return AppResponses.refund(orderNo, LocalDateTime.now().format(DAY_FORMATTER),
                    order.getOrderAmount(), "FAIL", "退款失败", refundNotifyUrl);
        }
        if (order.getOrderAmount() == null || order.getOrderAmount() <= 0) {
            log.error("APP 请求退款 订单金额非法, orderNo={}, amount={}", orderNo, order.getOrderAmount());
            return AppResponses.failMessage("订单金额异常，请联系工作人员");
        }
        int unsettled = orderMapper.countUnsettledRefunds(orderNo);
        if (unsettled > 0) {
            log.warn("APP 请求退款 该订单尚有未收口的退款单，拒绝再退, orderNo={}, unsettled={}", orderNo, unsettled);
            return AppResponses.refund(orderNo, LocalDateTime.now().format(DAY_FORMATTER),
                    order.getOrderAmount(), "PROCESSING", "退款处理中", refundNotifyUrl);
        }
        long refunded = order.getRefundAmount() == null ? 0L : order.getRefundAmount();
        long refundable = order.getOrderAmount() - refunded;
        if (refundable <= 0) {
            log.info("APP 请求退款 已退满，无可退金额, orderNo={}, orderAmount={}, refundAmount={}",
                    orderNo, order.getOrderAmount(), refunded);
            return AppResponses.refund(orderNo, LocalDateTime.now().format(DAY_FORMATTER),
                    order.getOrderAmount(), "FAIL", "退款失败", refundNotifyUrl);
        }

        RefundCommand command = new RefundCommand(orderNo, null, F2fRefundService.SOURCE_APP_REQUEST,
                refundable, null, "APP申请退款", null, request.getUserId(),
                order.getTransType(), null, payCenterOrderNoOf(orderNo));
        RefundOutcome outcome = refundService.refund(command);
        if (outcome.isRejected()) {
            log.warn("APP 请求退款被拒绝, orderNo={}, reason={}", orderNo, outcome.failureReason());
            return AppResponses.failMessage(outcome.failureReason());
        }
        log.info("APP 退款已受理, orderNo={}, refundNo={}, alreadyExisted={}",
                orderNo, outcome.refundNo(), outcome.alreadyExisted());
        return AppResponses.refund(orderNo, LocalDateTime.now().format(DAY_FORMATTER),
                order.getOrderAmount(), "PROCESSING", "退款进行中", refundNotifyUrl);
    }
    /** 退款结果查询。 */
    public JSONObject queryRefundResult(RequestAppPayResultReqDTO request) {
        String key = request.getOrderNo();
        F2fRefund refund = refundMapper.selectByRefundNo(key);
        if (refund == null) {
            List<F2fRefund> refunds = refundMapper.selectByOrigOrderNo(key);
            if (refunds != null && !refunds.isEmpty()) {
                refund = refunds.get(0);
            }
        }
        if (refund == null) {
            log.info("APP 退款结果查询 没有找到对应退款记录, key={}", key);
            return AppResponses.failMessage("没有找到对应退款记录");
        }
        String refundResult;
        String desc;
        if (F2fRefundService.STATUS_SUCCESS.equals(refund.getRefundStatus())) {
            refundResult = "SUCCESS";
            desc = "退款成功";
        } else if (F2fRefundService.STATUS_FAILED.equals(refund.getRefundStatus())) {
            refundResult = "FAIL";
            desc = "退款失败";
        } else {
            refundResult = "PROCESSING";
            desc = "退款中";
        }
        String refundDate = (refund.getFinishTms() == null
                ? refund.getRequestTms() : refund.getFinishTms()).format(DAY_FORMATTER);
        return AppResponses.refund(key, refundDate, refund.getRefundAmount(),
                refundResult, desc, null);
    }
    /** 支付中心退款结果回调。 */
    public JSONObject receiveRefundResult(AppRefundNotiResultReqDTO request) {
        F2fRefund refund = refundMapper.selectByRefundNo(request.getRefundNo());
        if (refund == null) {
            log.warn("退款回调 退款单不存在, refundNo={}", request.getRefundNo());
            return AppResponses.failMessage("订单号错误");
        }
        String status = refund.getRefundStatus();
        if (F2fRefundService.STATUS_SUCCESS.equals(status) || F2fRefundService.STATUS_FAILED.equals(status)) {
            log.info("退款回调重复到达，已是终态直接回成功, refundNo={}, status={}",
                    request.getRefundNo(), status);
            return AppResponses.success();
        }
        if (!request.isSuccess() && !request.isFailed()) {
            log.warn("退款回调 refundResult 取值不识别，不动状态, refundNo={}, refundResult={}",
                    request.getRefundNo(), request.getRefundResult());
            return AppResponses.success();
        }
        String toStatus = request.isSuccess()
                ? F2fRefundService.STATUS_SUCCESS : F2fRefundService.STATUS_FAILED;
        int updated = refundMapper.updateStatus(request.getRefundNo(),
                List.of(F2fRefundService.STATUS_INIT, F2fRefundService.STATUS_PROCESSING,
                        F2fRefundService.STATUS_MANUAL),
                toStatus, request.getOutRefundNo(), LocalDateTime.now());
        if (updated == 0) {
            log.info("退款回调 状态已被其他路径收口，幂等返回成功, refundNo={}", request.getRefundNo());
            return AppResponses.success();
        }
        int summaryRows = orderMapper.updateRefundSummary(refund.getOrigOrderNo());
        log.info("退款回调 订单退款汇总已重算, orderNo={}, refundNo={}, updatedRows={}",
                refund.getOrigOrderNo(), request.getRefundNo(), summaryRows);
        enqueueRefundNotify(refund, request);
        log.info("退款回调处理完成, refundNo={}, toStatus={}", request.getRefundNo(), toStatus);
        return AppResponses.success();
    }
    /** 入队一条退款结果通知。 */
    private void enqueueRefundNotify(F2fRefund refund, AppRefundNotiResultReqDTO request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", refund.getOrigOrderNo());
        payload.put("refundType", "00");
        payload.put("refundResult", request.getRefundResult());
        payload.put("refundResultDesc", request.getRefundResultDesc());
        payload.put("refundDate", request.getRefundDate() == null
                ? LocalDateTime.now().format(DAY_FORMATTER) : request.getRefundDate());
        payload.put("refundAmount", refund.getRefundAmount());
        boolean enqueued = notifyService.enqueue(F2fNotifyService.TYPE_REFUND_RESULT,
                refund.getOrigOrderNo(), refund.getRefundNo(), payload);
        if (!enqueued) {
            log.info("退款结果通知已存在，跳过入队, refundNo={}", refund.getRefundNo());
        }
    }

    /** 支付中心侧订单号，供退款报文的 {@code tradeNo} 使用；未支付时为 null。 */
    private String payCenterOrderNoOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayCenterOrderNo();
    }
}
