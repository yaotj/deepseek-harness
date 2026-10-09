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

/**
 * 支付中心退款结果回调收口（网关文档 §5.2）。
 *
 * <p>本类只做三件事：**按键定位退款单 → 按 `refundResult` 分支决定是否推进 → 留证据**。
 * 真正的状态推进全部委托 {@link DailyTicketRefundSettlementService}，因此同一套终态逻辑
 * 与「发起侧同步应答」「回查 / 重试」共用一份实现，NEVER 在这里另写一遍。
 *
 * <p>三条不可放宽的判据：
 * <ol>
 *   <li><b>受理白名单</b>：成功结果只接受 {@code REFUNDING} / {@code WAIT_VERIFY} / {@code FAILED}
 *       三个前置态（{@code FAILED} 属「按支付中心结论纠正」，会打 WARN）。其余状态一律拒收，
 *       符合 AGENTS.md §5.2「状态机校验用白名单」。</li>
 *   <li><b>重复到达一律幂等返成功</b>：已 {@code REFUNDED} 再收成功 → 只补平台退款单号并重推通知；
 *       已 {@code FAILED} 再收失败 → 直接返成功。<b>NEVER 改成报错</b>，否则支付中心会一直重推。</li>
 *   <li><b>已 REFUNDED 却收到失败结果时保持已退款</b>，只打 WARN 转人工 ——
 *       钱已经退了，把状态改回 FAILED 会让对账与 APP 两头都错。</li>
 * </ol>
 *
 * <p>另有一条易被当成缺陷的分支：回调带的 {@code orderNo}（支付订单号）与本单
 * {@code PAYMENT_ORDER_NO} 不一致时**只记日志、不改任何业务单**并返成功 ——
 * 那通常是同一笔业务的重复支付里另一笔的退款回调，改本单会退错账。
 */
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

    /**
     * 契约 §5.2 退款回调带 {@code refundAmount}（单位分、字符串形态），与本地 {@code DAILY_TICKET_REFUND.REFUND_AMOUNT} 比对。
     *
     * <p>不一致只打 WARN，**NEVER 因此拒绝回调或改状态** —— 部分退款口径未定，拒绝会让退款单永久空转；
     * 解析失败同样只打 WARN。
     */
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

    /** 把回调字段翻译成 {@code markRefunded} / {@code markRefundFailed} 认的 refundData 形状。 */
    Map<String, Object> toRefundCallbackData(DailyTicketRefundCallbackReqDTO request) {
        Map<String, Object> refundData = new LinkedHashMap<>();
        DailyTicketOrderSupport.putIfText(refundData, "refundNo", request.getRefundNo());
        DailyTicketOrderSupport.putIfText(refundData, "refundTime", request.getRefundDate());
        return refundData;
    }
}
