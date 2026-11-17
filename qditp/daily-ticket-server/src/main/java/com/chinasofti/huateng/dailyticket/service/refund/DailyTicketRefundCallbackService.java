package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.DailyTicketRefundNotifyService;
import com.chinasofti.huateng.dailyticket.service.paylog.DailyTicketPayLogWriter;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundCallbackReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;


@Service
public class DailyTicketRefundCallbackService {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketRefundCallbackService.class);

    private final DailyTicketOrderMapper orderMapper;
    private final TravelTicketOrderMapper travelOrderMapper;
    private final DailyTicketRefundMapper refundMapper;
    private final DailyTicketPayLogWriter payLogWriter;
    private final DailyTicketRefundSettlementService refundSettlementService;
    private final DailyTicketRefundNotifyService refundNotifyService;

    public DailyTicketRefundCallbackService(DailyTicketOrderMapper orderMapper,
                                            TravelTicketOrderMapper travelOrderMapper,
                                            DailyTicketRefundMapper refundMapper,
                                            DailyTicketPayLogWriter payLogWriter,
                                            DailyTicketRefundSettlementService refundSettlementService,
                                            DailyTicketRefundNotifyService refundNotifyService) {
        this.orderMapper = orderMapper;
        this.travelOrderMapper = travelOrderMapper;
        this.refundMapper = refundMapper;
        this.payLogWriter = payLogWriter;
        this.refundSettlementService = refundSettlementService;
        this.refundNotifyService = refundNotifyService;
    }

    public DailyTicketBaseResult receiveRefundResult(DailyTicketRefundCallbackReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (request == null) {
            return DailyTicketOrderSupport.fail(result, "退款回调报文为空");
        }
        String outRefundNo = request.getOutRefundNo();
        String merchantOrderNo = request.getMerchantOrderNo();
        String payOrderNo = request.getOrderNo();
        if (!StringUtils.hasText(outRefundNo) && !StringUtils.hasText(merchantOrderNo)) {
            log.warn("日票退款回调：缺少可定位退款单的键 payOrderNo={}", payOrderNo);
            return DailyTicketOrderSupport.fail(result, "outRefundNo与merchantOrderNo不能同时为空");
        }

        DailyTicketRefund refund = StringUtils.hasText(outRefundNo)
                ? refundMapper.selectByRefundOrderNo(outRefundNo) : null;
        if (refund == null && StringUtils.hasText(merchantOrderNo)) {
            refund = refundMapper.selectByOrderNo(merchantOrderNo);
        }
        String orderNo = refund != null ? refund.getOrderNo()
                : (StringUtils.hasText(merchantOrderNo) ? merchantOrderNo : outRefundNo);
        DailyTicketOrder order = refund == null ? null : orderMapper.selectByOrderNo(refund.getOrderNo());
        TravelTicketOrder travelOrder = refund != null
                && DailyTicketOrderSupport.ORDER_TYPE_TRAVEL_TICKET.equals(refund.getOrderType())
                ? travelOrderMapper.selectByOrderNo(refund.getParentOrderNo() == null
                        ? refund.getOrderNo() : refund.getParentOrderNo())
                : null;
        if ((order == null && travelOrder == null) || refund == null) {
            log.warn("日票退款回调：订单或退款单不存在 outRefundNo={}, merchantOrderNo={}, payOrderNo={}, orderExists={}, refundExists={}",
                    outRefundNo, merchantOrderNo, payOrderNo, order != null, refund != null);
            DailyTicketOrderSupport.fail(result, "退款记录不存在");
            payLogWriter.insert(orderNo, "REFUND_CALLBACK",
                    order == null ? null : order.getPayChannelCode(), request, result);
            return result;
        }
        String payChannelCode = order == null ? travelOrder.getPayChannelCode() : order.getPayChannelCode();

        String expectedPayOrderNo = order != null ? order.getPaymentOrderNo() : travelOrder.getPaymentOrderNo();
        if (StringUtils.hasText(payOrderNo) && StringUtils.hasText(expectedPayOrderNo)
                && !payOrderNo.equals(expectedPayOrderNo)) {
            log.warn("日票退款回调：支付订单号与本单不一致，疑似重复支付的另一笔支付，不更新业务单 "
                            + "orderNo={}, refundOrderNo={}, callbackPayOrderNo={}, orderPayOrderNo={}",
                    orderNo, refund.getRefundOrderNo(), payOrderNo, expectedPayOrderNo);
            DailyTicketOrderSupport.success(result);
            payLogWriter.insert(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
            return result;
        }

        warnIfRefundAmountMismatch(orderNo, refund, request.getRefundAmount());

        String refundStatus = refund.getRefundStatus();
        Map<String, Object> refundData = toRefundCallbackData(request);
        String refundResult = request.getRefundResult();
        if ("SUCCESS".equalsIgnoreCase(refundResult)) {
            if ("REFUNDED".equals(refundStatus)) {
                refundSettlementService.persistPlatformRefundNoIfChanged(refund, refundData);
                log.info("日票退款回调重复到达且已是 REFUNDED，仅补齐平台退款单号与 APP 通知 orderNo={}, platformRefundNo={}",
                        orderNo, refund.getPlatformRefundNo());
                DailyTicketOrderSupport.success(result);
                payLogWriter.insert(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
                refundNotifyService.deliverOne(refund.getOrderNo());
                return result;
            }
            if (!"REFUNDING".equals(refundStatus) && !"WAIT_VERIFY".equals(refundStatus)
                    && !"FAILED".equals(refundStatus)) {
                log.warn("日票退款回调：退款单状态不在受理白名单内 orderNo={}, refundStatus={}", orderNo, refundStatus);
                DailyTicketOrderSupport.fail(result, "退款单状态不允许收口: " + refundStatus);
                payLogWriter.insert(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
                return result;
            }
            if ("FAILED".equals(refundStatus)) {
                log.warn("日票退款回调：退款单原为 FAILED，按支付中心的成功结果纠正 orderNo={}, refundOrderNo={}",
                        orderNo, refund.getRefundOrderNo());
            }
            if (travelOrder != null) {
                refundSettlementService.markTravelRefunded(travelOrder, refund, refundData);
            } else {
                refundSettlementService.markRefunded(order, refund, refundData);
            }
            DailyTicketOrderSupport.success(result);
        } else if ("FAIL".equalsIgnoreCase(refundResult) || "FAILED".equalsIgnoreCase(refundResult)) {
            if ("REFUNDED".equals(refundStatus)) {
                log.warn("日票退款回调：本单已 REFUNDED 却收到失败结果，保持已退款状态并转人工核对 orderNo={}, refundOrderNo={}",
                        orderNo, refund.getRefundOrderNo());
                DailyTicketOrderSupport.success(result);
                payLogWriter.insert(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
                return result;
            }
            if ("FAILED".equals(refundStatus)) {
                log.info("日票退款回调重复到达且已是 FAILED，幂等返回 orderNo={}", orderNo);
                DailyTicketOrderSupport.success(result);
                payLogWriter.insert(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
                return result;
            }
            if (travelOrder != null) {
                refundSettlementService.markTravelRefundFailed(travelOrder, refund, refundData);
            } else {
                refundSettlementService.markRefundFailed(order, refund, refundData);
            }
            DailyTicketOrderSupport.success(result);
        } else if ("PROCESSING".equalsIgnoreCase(refundResult)) {
            refundSettlementService.persistPlatformRefundNoIfChanged(refund, refundData);
            log.info("日票退款回调为处理中，仅回填平台退款单号 orderNo={}, platformRefundNo={}",
                    orderNo, refund.getPlatformRefundNo());
            DailyTicketOrderSupport.success(result);
            payLogWriter.insert(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
            return result;
        } else {
            log.warn("日票退款回调：未知的退款结果，不推进状态 orderNo={}, refundResult={}", orderNo, refundResult);
            DailyTicketOrderSupport.success(result);
            payLogWriter.insert(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
            return result;
        }

        payLogWriter.insert(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
        refundNotifyService.deliverOne(refund.getOrderNo());
        return result;
    }


    void warnIfRefundAmountMismatch(String orderNo, DailyTicketRefund refund, String callbackAmount) {
        if (refund == null || !StringUtils.hasText(callbackAmount)) {
            return;
        }
        Integer localAmount = refund.getRefundAmount();
        if (localAmount == null) {
            return;
        }
        try {
            long callback = Long.parseLong(callbackAmount.trim());
            if (localAmount.longValue() != callback) {
                log.warn("日票退款回调：金额与本地退款单不一致，只告警不阻断 orderNo={}, refundOrderNo={}, localAmount={}, callbackAmount={}",
                        orderNo, refund.getRefundOrderNo(), localAmount, callback);
            }
        } catch (NumberFormatException e) {
            log.warn("日票退款回调：refundAmount 不是整数分，跳过金额比对 orderNo={}, refundOrderNo={}, refundAmount={}",
                    orderNo, refund.getRefundOrderNo(), callbackAmount);
        }
    }

    Map<String, Object> toRefundCallbackData(DailyTicketRefundCallbackReqDTO request) {
        Map<String, Object> refundData = new LinkedHashMap<>();
        DailyTicketOrderSupport.putIfText(refundData, "refundNo", request.getRefundNo());
        DailyTicketOrderSupport.putIfText(refundData, "refundTime", request.getRefundDate());
        return refundData;
    }
}
