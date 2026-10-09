package com.chinasofti.huateng.dailyticket.service.refund;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayClient;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.paylog.DailyTicketPayLogWriter;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 已取消订单的自动退款：取消动作落在未激活的**已收款**订单上时，由本类把钱退回去。
 *
 * <p>两个触发点，语义完全相同、**NEVER 只接其中一个**：
 * <ul>
 *   <li>取消订单时订单已是 {@code PAID}（{@code DailyTicketOrderCreationService.cancelOrder}）；</li>
 *   <li>已取消的订单随后收到支付成功通知（{@code DailyTicketPaymentService.receivePayResult}）——
 *       取消与支付赛跑时钱可能晚到，这一支是需求里「取消状态订单收到支付结果通知需自动发起退款」的落点。</li>
 * </ul>
 *
 * <p><b>本类不注入 {@code DailyTicketPaymentService}，NEVER 加</b>：支付服务要注入本类，
 * 反向再注一次就是构造器循环依赖，Spring 直接起不来。依赖方向固定为
 * {@code order / payment → canceledRefund}。
 *
 * <p><b>零 {@code @Transactional}、NEVER 加</b>：链路里有支付中心出网调用，
 * 理由同 {@link DailyTicketRefundSettlementService} 类注释（AGENTS.md §5.2 两条）。
 *
 * <p><b>与手工退款 {@code DailyTicketRefundInitiationService.requestRefundTicket} 的差别只在前置状态</b>：
 * 那条要求订单 {@code PAID + PAID}，本条要求 {@code CANCELED + PAY_STATUS=PAID}。
 * 收口段完全复用 {@link DailyTicketRefundSettlementService}，因此 IF8B-04 退款结果通知自动走它的 outbox。
 *
 * <p><b>失败处置：退款单与订单都留在 {@code REFUNDING}，NEVER 回写成 {@code PAID}</b>。
 * 取消单回到 {@code PAID} 等于「未激活 + 已支付」，会重新变成可激活状态——票已经取消了还能用。
 * 出网未成功的单交给运营页 {@code /page/daily-ticket/refund/pay-query} 与 {@code /retry} 人工收口。
 */
@Service
public class CanceledOrderRefundService {
    private static final Logger log = LoggerFactory.getLogger(CanceledOrderRefundService.class);

    private static final String ORDER_STATUS_CANCELED = "CANCELED";
    private static final String ORDER_STATUS_REFUNDING = "REFUNDING";
    private static final String PAY_STATUS_PAID = "PAID";
    private static final String REFUND_REASON = "订单取消自动退款";

    private final DailyTicketPayGatewayClient payGatewayClient;
    private final DailyTicketOrderMapper orderMapper;
    private final TravelTicketOrderMapper travelOrderMapper;
    private final DailyTicketInstanceMapper instanceMapper;
    private final DailyTicketRefundMapper refundMapper;
    private final DailyTicketRefundSettlementService refundSettlementService;
    private final RefundGatewayRequests refundGatewayRequests;
    private final DailyTicketPayLogWriter payLogWriter;

    public CanceledOrderRefundService(DailyTicketPayGatewayClient payGatewayClient,
                                      DailyTicketOrderMapper orderMapper,
                                      TravelTicketOrderMapper travelOrderMapper,
                                      DailyTicketInstanceMapper instanceMapper,
                                      DailyTicketRefundMapper refundMapper,
                                      DailyTicketRefundSettlementService refundSettlementService,
                                      RefundGatewayRequests refundGatewayRequests,
                                      DailyTicketPayLogWriter payLogWriter) {
        this.payGatewayClient = payGatewayClient;
        this.orderMapper = orderMapper;
        this.travelOrderMapper = travelOrderMapper;
        this.instanceMapper = instanceMapper;
        this.refundMapper = refundMapper;
        this.refundSettlementService = refundSettlementService;
        this.refundGatewayRequests = refundGatewayRequests;
        this.payLogWriter = payLogWriter;
    }

    /**
     * 独立日票（含多日计次票）取消后自动退款。
     *
     * @return {@code true} 表示退款单已落库（无论出网结果如何）；{@code false} 表示本单不该退或退不了
     */
    public boolean refundCanceledDailyOrder(String orderNo) {
        DailyTicketOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.warn("取消单自动退款：订单不存在 orderNo={}", orderNo);
            return false;
        }
        if (!ORDER_STATUS_CANCELED.equals(order.getOrderStatus())) {
            log.info("取消单自动退款：订单已不在取消态，跳过 orderNo={}, orderStatus={}",
                    orderNo, order.getOrderStatus());
            return false;
        }
        if (!PAY_STATUS_PAID.equals(order.getPayStatus())) {
            log.info("取消单自动退款：订单未收款，无需退款 orderNo={}, payStatus={}", orderNo, order.getPayStatus());
            return false;
        }
        if (DailyTicketOrderSupport.isFreeOrder(order)) {
            log.info("取消单自动退款：免费票无款可退 orderNo={}", orderNo);
            return false;
        }
        if (!StringUtils.hasText(order.getPaymentOrderNo())) {
            log.error("取消单自动退款：原支付订单号缺失，无法退款，转人工处理 orderNo={}", orderNo);
            return false;
        }
        if (instanceMapper.selectByOrderNo(orderNo) != null) {
            log.error("取消单自动退款：车票已激活，不做自动退款，转人工处理 orderNo={}", orderNo);
            return false;
        }

        DailyTicketRefund existing = refundMapper.selectByOrderNo(orderNo);
        if (existing != null) {
            log.info("取消单自动退款：退款单已存在，幂等跳过 orderNo={}, refundStatus={}",
                    orderNo, existing.getRefundStatus());
            return true;
        }

        DailyTicketRefund refund = buildDailyRefund(order);
        try {
            refundMapper.insert(refund);
        } catch (RuntimeException e) {
            if (!DailyTicketRefundMessages.isDuplicateRefund(e)) {
                throw e;
            }
            log.warn("取消单自动退款并发命中唯一索引，按已有退款单收口 orderNo={}", orderNo);
            return true;
        }
        orderMapper.updateOrderStatus(orderNo, ORDER_STATUS_REFUNDING);

        if (DailyTicketOrderSupport.isCxuhOrderSource(order.getOrderSource())) {
            log.info("取消单自动退款：小程序渠道单只落退款单，等对方同步退款结果 orderNo={}", orderNo);
            return true;
        }

        Map<String, Object> refundRequest = refundGatewayRequests.buildDailyTicketRefund(order, refund);
        log.info("取消单自动退款准备调用支付网关退款接口 orderNo={}, request={}",
                orderNo, JSON.toJSONString(refundRequest));
        DailyTicketPayGatewayResponse response;
        try {
            response = payGatewayClient.requestRefund(refundRequest);
        } catch (RuntimeException e) {
            payLogWriter.insert(orderNo, "REFUND", order.getPayChannelCode(), refundRequest,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("取消单自动退款网关调用异常，退款单留 REFUNDING 等回查 / 人工重试 orderNo={}", orderNo, e);
            return true;
        }
        payLogWriter.insert(orderNo, "REFUND", order.getPayChannelCode(), refundRequest, response);
        if (!DailyTicketRefundMessages.isGatewaySuccess(response)) {
            log.error("取消单自动退款网关未返成功，退款单留 REFUNDING 等回查 / 人工重试 orderNo={}, response={}",
                    orderNo, JSON.toJSONString(response));
            return true;
        }

        String refundTime = response.getData() == null ? null
                : DailyTicketOrderSupport.stringValue(response.getData().get("refundTime"), null);
        if (StringUtils.hasText(refundTime)) {
            refundSettlementService.markRefunded(order, refund, response.getData());
        } else {
            refundSettlementService.updatePlatformRefundNo(refund, response.getData());
            refundSettlementService.markRefunding(order, refund);
        }
        log.info("取消单自动退款完成 orderNo={}, refundOrderNo={}, refundTime={}",
                orderNo, refund.getRefundOrderNo(), refundTime);
        return true;
    }

    /**
     * 旅游票主单取消后自动整单退款（{@code REFUND_SCOPE=TRAVEL_FULL}）。
     *
     * <p>钱是按主单那一笔收的（{@code requestTravelPay} 送的 {@code merchantOrderNo} 是主单号），
     * 因此退款也只能按主单整单退，**NEVER 改成逐子单退**。
     */
    public boolean refundCanceledTravelOrder(String parentOrderNo) {
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(parentOrderNo);
        if (parent == null) {
            log.warn("取消单自动退款：旅游票主订单不存在 parentOrderNo={}", parentOrderNo);
            return false;
        }
        if (!ORDER_STATUS_CANCELED.equals(parent.getOrderStatus())) {
            log.info("取消单自动退款：旅游票主单已不在取消态，跳过 parentOrderNo={}, orderStatus={}",
                    parentOrderNo, parent.getOrderStatus());
            return false;
        }
        if (!PAY_STATUS_PAID.equals(parent.getPayStatus())) {
            log.info("取消单自动退款：旅游票主单未收款，无需退款 parentOrderNo={}, payStatus={}",
                    parentOrderNo, parent.getPayStatus());
            return false;
        }
        if (DailyTicketOrderSupport.isFreeOrder(parent)) {
            log.info("取消单自动退款：免费旅游票无款可退 parentOrderNo={}", parentOrderNo);
            return false;
        }
        if (!StringUtils.hasText(parent.getPaymentOrderNo())) {
            log.error("取消单自动退款：旅游票原支付订单号缺失，转人工处理 parentOrderNo={}", parentOrderNo);
            return false;
        }

        List<DailyTicketOrder> children = orderMapper.selectByParentOrderNo(parentOrderNo);
        if (children == null || children.isEmpty()) {
            log.error("取消单自动退款：旅游票子单缺失，转人工处理 parentOrderNo={}", parentOrderNo);
            return false;
        }
        for (DailyTicketOrder child : children) {
            if (instanceMapper.selectByOrderNo(child.getOrderNo()) != null) {
                log.error("取消单自动退款：旅游票存在已激活子单，不做自动退款，转人工处理 parentOrderNo={}, subOrderNo={}",
                        parentOrderNo, child.getOrderNo());
                return false;
            }
        }

        DailyTicketRefund existing = refundMapper.selectByOrderNo(parentOrderNo);
        if (existing != null) {
            log.info("取消单自动退款：旅游票退款单已存在，幂等跳过 parentOrderNo={}, refundStatus={}",
                    parentOrderNo, existing.getRefundStatus());
            return true;
        }

        DailyTicketRefund refund = buildTravelRefund(parent);
        try {
            refundMapper.insert(refund);
        } catch (RuntimeException e) {
            if (!DailyTicketRefundMessages.isDuplicateRefund(e)) {
                throw e;
            }
            log.warn("旅游票取消单自动退款并发命中唯一索引，按已有退款单收口 parentOrderNo={}", parentOrderNo);
            return true;
        }
        refundSettlementService.insertTravelRefundDetails(refund, parentOrderNo, children);
        travelOrderMapper.updateOrderStatus(parentOrderNo, ORDER_STATUS_REFUNDING);

        if (DailyTicketOrderSupport.isCxuhOrderSource(parent.getOrderSource())) {
            log.info("取消单自动退款：小程序渠道旅游票只落退款单，等对方同步退款结果 parentOrderNo={}", parentOrderNo);
            return true;
        }

        Map<String, Object> refundRequest = refundGatewayRequests.buildTravelRefund(parent, refund);
        log.info("旅游票取消单自动退款准备调用支付网关退款接口 parentOrderNo={}, request={}",
                parentOrderNo, JSON.toJSONString(refundRequest));
        DailyTicketPayGatewayResponse response;
        try {
            response = payGatewayClient.requestRefund(refundRequest);
        } catch (RuntimeException e) {
            payLogWriter.insert(parentOrderNo, "REFUND", parent.getPayChannelCode(), refundRequest,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            log.error("旅游票取消单自动退款网关调用异常，退款单留 REFUNDING 等回查 / 人工重试 parentOrderNo={}",
                    parentOrderNo, e);
            return true;
        }
        payLogWriter.insert(parentOrderNo, "REFUND", parent.getPayChannelCode(), refundRequest, response);
        if (!DailyTicketRefundMessages.isGatewaySuccess(response)) {
            log.error("旅游票取消单自动退款网关未返成功，退款单留 REFUNDING parentOrderNo={}, response={}",
                    parentOrderNo, JSON.toJSONString(response));
            return true;
        }

        String refundTime = response.getData() == null ? null
                : DailyTicketOrderSupport.stringValue(response.getData().get("refundTime"), null);
        if (StringUtils.hasText(refundTime)) {
            refundSettlementService.markTravelRefunded(parent, refund, response.getData());
        } else {
            refundSettlementService.updatePlatformRefundNo(refund, response.getData());
            refundSettlementService.markTravelRefunding(parentOrderNo, refund);
        }
        log.info("旅游票取消单自动退款完成 parentOrderNo={}, refundOrderNo={}, refundTime={}",
                parentOrderNo, refund.getRefundOrderNo(), refundTime);
        return true;
    }

    /**
     * 取消单退款一律是 {@code refundType=00} 直接退款、{@code REFUND_STATUS=REFUNDING}。
     *
     * <p>**NEVER 写成 {@code 01} 核验退款**：核验退款是给「已激活、要观察 5 天有没有乘坐」的票用的，
     * 而取消的前提就是未激活，没有可核验的行程。
     */
    private DailyTicketRefund buildDailyRefund(DailyTicketOrder order) {
        Date now = new Date();
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setId(DailyTicketOrderSupport.nextId());
        refund.setOrderNo(order.getOrderNo());
        refund.setOrderType(DailyTicketOrderSupport.ORDER_TYPE_DAILY_TICKET);
        refund.setRefundOrderNo(DailyTicketRefundMessages.buildRefundOrderNo(order.getOrderNo()));
        refund.setRefundAmount(order.getPayAmount() == null
                ? (order.getTicketPrice() == null ? 0 : order.getTicketPrice())
                : order.getPayAmount());
        refund.setRefundStatus(ORDER_STATUS_REFUNDING);
        refund.setRefundType("00");
        refund.setRefundReason(REFUND_REASON);
        refund.setVerifyAfterTime(null);
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        return refund;
    }

    private DailyTicketRefund buildTravelRefund(TravelTicketOrder parent) {
        Date now = new Date();
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setId(DailyTicketOrderSupport.nextId());
        refund.setOrderNo(parent.getOrderNo());
        refund.setOrderType(DailyTicketOrderSupport.ORDER_TYPE_TRAVEL_TICKET);
        refund.setRefundScope("TRAVEL_FULL");
        refund.setParentOrderNo(parent.getOrderNo());
        refund.setRefundOrderNo(DailyTicketRefundMessages.buildRefundOrderNo(parent.getOrderNo()));
        refund.setRefundAmount(parent.getPayAmount() == null
                ? (parent.getTotalAmount() == null ? 0 : parent.getTotalAmount())
                : parent.getPayAmount());
        refund.setRefundStatus(ORDER_STATUS_REFUNDING);
        refund.setRefundType("00");
        refund.setRefundReason(REFUND_REASON);
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        return refund;
    }
}
