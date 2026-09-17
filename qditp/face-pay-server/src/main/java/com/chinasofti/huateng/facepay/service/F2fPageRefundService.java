package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.entity.F2fRefund;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.mapper.F2fRefundMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 运营端人工退款。本类刻意不带 {@code @Transactional}（链路里有支付中心调用），NEVER 加。 */
@Service
public class F2fPageRefundService {

    private static final Logger log = LoggerFactory.getLogger(F2fPageRefundService.class);

    /** 允许人工退款的状态白名单。 */
    private static final List<String> REFUNDABLE = F2fOrderStatus.REFUNDABLE;

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fRefundMapper refundMapper;

    private final F2fRefundService refundService;

    public F2fPageRefundService(F2fOrderMapper orderMapper,
                               F2fPaymentMapper paymentMapper,
                               F2fRefundMapper refundMapper,
                               F2fRefundService refundService) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.refundMapper = refundMapper;
        this.refundService = refundService;
    }

    /**
     * 整单人工退款。
     *
     * @param operatorId 操作员，可空；落到 {@code F2F_REFUND.OPERATOR_ID}
     */
    public ResultVO<Map<String, Object>> refundWholeOrder(String orderNo, String reason, String operatorId) {
        return doRefund(orderNo, null, reason, operatorId);
    }

    /**
     * 按指定金额人工退款，对齐旧模块 {@code /page/app/orders/{orderNo}/refund} （{@code AppOrderService.refundByAmount}）。
     *
     * @param refundAmount 本次退款金额，单位分，MUST 大于 0 且不大于剩余可退金额
     */
    public ResultVO<Map<String, Object>> refundByAmount(String orderNo, Long refundAmount,
                                                        String reason, String operatorId) {
        if (refundAmount == null || refundAmount <= 0) {
            return ResultMapper.error("退款金额必须大于 0");
        }
        return doRefund(orderNo, refundAmount, reason, operatorId);
    }

    /**
     * 两个运营端退款入口的公共实现。
     *
     * @param requestedAmount 传 null 即「退剩余可退金额」（整单入口），
     */
    private ResultVO<Map<String, Object>> doRefund(String orderNo, Long requestedAmount,
                                                   String reason, String operatorId) {
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return ResultMapper.error("未找到对应的当面付订单");
        }
        F2fRefund existing = findPageRefund(orderNo);
        if (existing != null) {
            return ResultMapper.error("该订单已发起退款，退款单号：" + existing.getRefundNo());
        }
        int unsettled = orderMapper.countUnsettledRefunds(orderNo);
        if (unsettled > 0) {
            log.warn("运营端退款 该订单尚有未收口的退款单，拒绝再退, orderNo={}, unsettled={}", orderNo, unsettled);
            return ResultMapper.error("该订单有退款正在处理中，请等待处理完成后再操作");
        }
        if (!REFUNDABLE.contains(order.getOrderStatus())) {
            log.info("运营端退款 订单状态不允许, orderNo={}, status={}", orderNo, order.getOrderStatus());
            return ResultMapper.error("仅支付成功的订单可以退款");
        }
        if (order.getOrderAmount() == null || order.getOrderAmount() <= 0) {
            return ResultMapper.error("订单金额无效，不能退款");
        }
        long refunded = order.getRefundAmount() == null ? 0L : order.getRefundAmount();
        long refundable = order.getOrderAmount() - refunded;
        if (refundable <= 0) {
            log.info("运营端退款 已退满，无可退金额, orderNo={}, orderAmount={}, refundAmount={}",
                    orderNo, order.getOrderAmount(), refunded);
            return ResultMapper.error("该订单已全额退款，无可退金额");
        }
        long amount = requestedAmount == null ? refundable : requestedAmount;
        if (amount > refundable) {
            log.info("运营端退款 金额超出可退, orderNo={}, requested={}, refundable={}",
                    orderNo, amount, refundable);
            return ResultMapper.error("退款金额不能大于可退金额");
        }

        RefundCommand command = new RefundCommand(orderNo, null, F2fRefundService.SOURCE_PAGE_MANUAL,
                amount, null, reason, null, operatorId,
                order.getTransType(), null, payCenterOrderNoOf(orderNo));
        RefundOutcome outcome = refundService.refund(command);
        if (outcome.isRejected()) {
            log.warn("运营端退款被拒绝, orderNo={}, reason={}", orderNo, outcome.failureReason());
            return ResultMapper.error(outcome.failureReason());
        }
        log.info("运营端退款已受理, orderNo={}, refundNo={}, refundAmount={}, orderAmount={}, "
                        + "alreadyRefunded={}, refundStatus={}, operatorId={}, alreadyExisted={}",
                orderNo, outcome.refundNo(), amount, order.getOrderAmount(), refunded,
                outcome.refundStatus(), operatorId, outcome.alreadyExisted());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("retCode", "0000");
        data.put("retMsg", "成功");
        return ResultMapper.ok(data);
    }

    /** 找该订单已有的运营端退款单；其它来源（设备退款、批量退款）不算重复。 */
    private F2fRefund findPageRefund(String orderNo) {
        List<F2fRefund> refunds = refundMapper.selectByOrigOrderNo(orderNo);
        if (refunds == null) {
            return null;
        }
        for (F2fRefund refund : refunds) {
            if (F2fRefundService.SOURCE_PAGE_MANUAL.equals(refund.getRefundSource())) {
                return refund;
            }
        }
        return null;
    }

    private String payCenterOrderNoOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayCenterOrderNo();
    }
}
