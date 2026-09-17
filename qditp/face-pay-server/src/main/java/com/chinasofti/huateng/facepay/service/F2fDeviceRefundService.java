package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestRefundReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 设备侧主动退款（TVM {@code requestRefund}）。 */
@Service
public class F2fDeviceRefundService {

    /** 可退款的订单状态白名单：钱已收到、且业务已结束或已失败。 */
    private static final List<String> REFUNDABLE = F2fOrderStatus.REFUNDABLE;

    private static final Logger log = LoggerFactory.getLogger(F2fDeviceRefundService.class);

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fRefundService refundService;

    public F2fDeviceRefundService(F2fOrderMapper orderMapper, F2fPaymentMapper paymentMapper,
                                  F2fRefundService refundService) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.refundService = refundService;
    }

    /** TVM 主动退款。 */
    public JSONObject requestRefund(RequestRefundReqDTO request) {
        Long amount = request.amountInFen();
        if (amount == null) {
            log.warn("TVM 退款金额非法, orderNo={}, refundAmt={}", request.getOrderNo(), request.getRefundAmt());
            return TvmResponses.refundFail("退款金额格式非法");
        }
        F2fOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.warn("TVM 退款 订单不存在, orderNo={}", request.getOrderNo());
            return TvmResponses.refundOrderNotFound();
        }
        if (!REFUNDABLE.contains(order.getOrderStatus())) {
            log.warn("TVM 退款 订单状态不可退, orderNo={}, status={}",
                    request.getOrderNo(), order.getOrderStatus());
            return TvmResponses.refundFail("订单状态不是支付成功,不能退款");
        }
        int unsettled = orderMapper.countUnsettledRefunds(request.getOrderNo());
        if (unsettled > 0) {
            log.warn("TVM 退款 该订单尚有未收口的退款单，拒绝再退, orderNo={}, unsettled={}",
                    request.getOrderNo(), unsettled);
            return TvmResponses.refundFail("该订单有退款正在处理中,请稍后再试");
        }
        if (order.getOrderAmount() != null) {
            long refunded = order.getRefundAmount() == null ? 0L : order.getRefundAmount();
            if (amount > order.getOrderAmount() - refunded) {
                log.error("TVM 退款金额超过可退金额被拒, orderNo={}, refundAmt={}, orderAmount={}, refunded={}",
                        request.getOrderNo(), amount, order.getOrderAmount(), refunded);
                return TvmResponses.refundFail("退款金额不能大于订单金额");
            }
        }

        F2fPayment payment = paymentMapper.selectLastAttempt(request.getOrderNo());
        RefundCommand command = new RefundCommand(request.getOrderNo(), null,
                F2fRefundService.SOURCE_TVM_REQUEST, amount, null,
                request.getRefundReason(), request.getDeviceId(), order.getOperatorId(),
                order.getTransType(), null, payment == null ? null : payment.getPayCenterOrderNo());
        RefundOutcome outcome = refundService.refund(command);

        if (outcome.isRejected()) {
            return TvmResponses.refundFail(outcome.failureReason());
        }
        if (outcome.alreadyExisted()) {
            log.info("TVM 退款幂等命中已有退款单, orderNo={}, refundNo={}, status={}",
                    request.getOrderNo(), outcome.refundNo(), outcome.refundStatus());
            return refundAccepted(outcome);
        }
        if (!outcome.isAccepted()) {
            log.error("TVM 退款未被支付中心受理，已落库待扫表重试, orderNo={}, refundNo={}",
                    request.getOrderNo(), outcome.refundNo());
            return TvmResponses.refundFail("退款请求已受理，结果稍后确认");
        }
        log.info("TVM 退款已受理, orderNo={}, refundNo={}", request.getOrderNo(), outcome.refundNo());
        return refundAccepted(outcome);
    }

    /** 把退款单当前状态翻译成设备侧的 {@code refundResult}。 */
    private static JSONObject refundAccepted(RefundOutcome outcome) {
        boolean success = F2fRefundService.STATUS_SUCCESS.equals(outcome.refundStatus());
        return TvmResponses.refundSuccess(success ? "SUCCESS" : "PROCESSING",
                success ? "退款成功" : "退款处理中", outcome.refundNo());
    }
}
