package com.chinasofti.huateng.dailyticket.service.refund;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayClient;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.payment.DailyTicketPaymentService;
import com.chinasofti.huateng.dailyticket.service.paylog.DailyTicketPayLogWriter;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketInstanceStatus;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.dailyticket.service.travel.TravelParentSummaryWriter;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class DailyTicketRefundInitiationService {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketRefundInitiationService.class);

    private static final String ORDER_TYPE_DAILY_TICKET = DailyTicketOrderSupport.ORDER_TYPE_DAILY_TICKET;
    private static final String ORDER_TYPE_TRAVEL_TICKET = DailyTicketOrderSupport.ORDER_TYPE_TRAVEL_TICKET;
    private static final String TICKET_STATUS_ACTIVATED = DailyTicketInstanceStatus.ACTIVATED;
    private static final String TICKET_STATUS_USED = DailyTicketInstanceStatus.USED;
    private static final String TICKET_STATUS_EXPIRED = DailyTicketInstanceStatus.EXPIRED;
    private static final String TICKET_STATUS_REFUNDED = DailyTicketInstanceStatus.REFUNDED;

    private final DailyTicketPayGatewayClient payGatewayClient;
    private final DailyTicketOrderMapper orderMapper;
    private final TravelTicketOrderMapper travelOrderMapper;
    private final DailyTicketInstanceMapper instanceMapper;
    private final DailyTicketRefundMapper refundMapper;
    private final DailyTicketTicketLockWriter ticketLockWriter;
    private final TravelParentSummaryWriter travelParentSummaryWriter;
    private final DailyTicketPayLogWriter payLogWriter;
    private final DailyTicketRefundSettlementService refundSettlementService;
    private final RefundGatewayRequests refundGatewayRequests;
    private final DailyTicketPaymentService paymentService;

    public DailyTicketRefundInitiationService(DailyTicketPayGatewayClient payGatewayClient,
                                               DailyTicketOrderMapper orderMapper,
                                               TravelTicketOrderMapper travelOrderMapper,
                                               DailyTicketInstanceMapper instanceMapper,
                                               DailyTicketRefundMapper refundMapper,
                                               DailyTicketTicketLockWriter ticketLockWriter,
                                               TravelParentSummaryWriter travelParentSummaryWriter,
                                               DailyTicketPayLogWriter payLogWriter,
                                               DailyTicketRefundSettlementService refundSettlementService,
                                               RefundGatewayRequests refundGatewayRequests,
                                               DailyTicketPaymentService paymentService) {
        this.payGatewayClient = payGatewayClient;
        this.orderMapper = orderMapper;
        this.travelOrderMapper = travelOrderMapper;
        this.instanceMapper = instanceMapper;
        this.refundMapper = refundMapper;
        this.ticketLockWriter = ticketLockWriter;
        this.travelParentSummaryWriter = travelParentSummaryWriter;
        this.payLogWriter = payLogWriter;
        this.refundSettlementService = refundSettlementService;
        this.refundGatewayRequests = refundGatewayRequests;
        this.paymentService = paymentService;
    }

    public DailyTicketRefundResult requestRefundTicket(DailyTicketOrderNoReqDTO request) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        String validMsg = validateOrderNo(request == null ? null : request.getOrderNo(), request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return fail(result, validMsg);
        }
        if (ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return requestTravelRefund(request.getOrderNo());
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        if (isFreeOrder(order)) {
            return fail(result, "免费订单不支持退款");
        }
        DailyTicketRefund existingRefund = refundMapper.selectByOrderNo(order.getOrderNo());
        if (existingRefund != null) {
            return buildExistingRefundResult(result, existingRefund);
        }
        if (!"PAID".equals(order.getOrderStatus()) || !"PAID".equals(order.getPayStatus())) {
            return fail(result, "订单未支付成功，不允许退款");
        }
        if (!StringUtils.hasText(order.getPaymentOrderNo())) {
            paymentService.queryAndRefreshPayResult(order);
            order = orderMapper.selectByOrderNo(order.getOrderNo());
        }
        if (order == null || !StringUtils.hasText(order.getPaymentOrderNo())) {
            return fail(result, "原支付订单号缺失，不允许退款");
        }

        DailyTicketInstance ticket = instanceMapper.selectByOrderNo(order.getOrderNo());
        if (ticket != null && !TICKET_STATUS_ACTIVATED.equals(ticket.getTicketStatus())) {
            return fail(result, "车票已使用，不允许退款");
        }

        String refundType = ticket == null ? "00" : "01";
        DailyTicketRefund refund = buildRefund(order, refundType);
        boolean cxuhOrder = isCxuhOrderSource(order.getOrderSource());
        if (cxuhOrder) {
            refund.setRefundStatus("REFUNDING");
            refund.setVerifyAfterTime(null);
        }

        if ("01".equals(refundType)) {
            if (!lockTicketForRefund(ticket)) {
                log.warn("日票核验退款：锁票失败，车票状态已变更 orderNo={}, instanceId={}",
                        order.getOrderNo(), ticket.getId());
                return fail(result, "车票状态已变更，不允许退款");
            }
            try {
                refundMapper.insert(refund);
                orderMapper.updateOrderStatus(order.getOrderNo(), "REFUNDING");
            } catch (RuntimeException e) {
                if (isDuplicateRefund(e)) {
                    DailyTicketRefund existing = refundMapper.selectByOrderNo(order.getOrderNo());
                    if (existing != null) {
                        log.warn("日票核验退款并发命中唯一索引，回查已有退款单收口 orderNo={}", order.getOrderNo());
                        return buildExistingRefundResult(result, existing);
                    }
                }
                releaseTicketLock(order.getOrderNo());
                log.error("日票核验退款落库失败，已把车票的 REFUND_LOCKED 放回，NEVER 让失败请求把票锁死 orderNo={}",
                        order.getOrderNo(), e);
                throw e;
            }
            result.setRefundType(refundType);
            result.setOrderNo(order.getOrderNo());
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc(cxuhOrder ? "退款已受理，等待小程序同步退款结果" : "已激活车票进入5天核验退款流程");
            result.setRefundAmount(String.valueOf(refund.getRefundAmount()));
            return success(result);
        }
        try {
            refundMapper.insert(refund);
        } catch (RuntimeException e) {
            if (!isDuplicateRefund(e)) {
                throw e;
            }
            DailyTicketRefund existing = refundMapper.selectByOrderNo(order.getOrderNo());
            if (existing != null) {
                log.warn("日票退款并发命中唯一索引，回查已有退款单收口 orderNo={}", order.getOrderNo());
                return buildExistingRefundResult(result, existing);
            }
            throw e;
        }
        if (cxuhOrder) {
            orderMapper.updateOrderStatus(order.getOrderNo(), "REFUNDING");
            result.setRefundType(refundType);
            result.setOrderNo(refund.getRefundOrderNo());
            result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc("退款已受理，等待小程序同步退款结果");
            return success(result);
        }

        Map<String, Object> refundReq = buildDailyTicketRefundRequest(order, refund);
        log.info("日票服务准备调用支付网关退款接口 orderNo={}, request={}", order.getOrderNo(), JSON.toJSONString(refundReq));
        DailyTicketPayGatewayResponse refundResponse;
        try {
            refundResponse = payGatewayClient.requestRefund(refundReq);
        } catch (RuntimeException e) {
            log.error("日票退款网关调用异常，已留证据并置 REFUNDING 等收口 orderNo={}", order.getOrderNo(), e);
            insertPayLog(order.getOrderNo(), "REFUND", order.getPayChannelCode(), refundReq,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            orderMapper.updateOrderStatus(order.getOrderNo(), "REFUNDING");
            result.setRefundType(refundType);
            result.setOrderNo(refund.getRefundOrderNo());
            result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc("退款已提交，结果待确认");
            return success(result);
        }
        log.info("日票服务调用支付网关退款接口完成 orderNo={}, response={}", order.getOrderNo(), JSON.toJSONString(refundResponse));
        insertPayLog(order.getOrderNo(), "REFUND", order.getPayChannelCode(), refundReq, refundResponse);
        if (!isGatewaySuccess(refundResponse)) {
            orderMapper.updateOrderStatus(order.getOrderNo(), "REFUNDING");
            return fail(result, refundResponse == null ? "日票退款网关调用失败" : refundResponse.getMsg());
        }

        String refundTime = refundResponse.getData() == null ? null
                : stringValue(refundResponse.getData().get("refundTime"), null);
        if (StringUtils.hasText(refundTime)) {
            markRefunded(order, refund, refundResponse.getData());
        } else {
            updatePlatformRefundNo(refund, refundResponse.getData());
            markRefunding(order, refund);
        }

        result.setRefundType(refundType);
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundResultDesc(StringUtils.hasText(refundTime) ? "退款完成" : "退款申请已提交，请查询退款结果");
        result.setRefundResult(StringUtils.hasText(refundTime) ? "SUCCESS" : "PROCESSING");
        result.setRefundDate(refundTime);
        result.setRefundAmount(String.valueOf(order.getTicketPrice() == null ? 0 : order.getTicketPrice()));
        return success(result);
    }

    private DailyTicketRefundResult requestTravelRefund(String parentOrderNo) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(parentOrderNo);
        if (parent == null) {
            return fail(result, "旅游票主订单不存在");
        }
        if (isFreeOrder(parent)) {
            return fail(result, "免费订单不支持退款");
        }
        DailyTicketRefund existing = refundMapper.selectByOrderNo(parentOrderNo);
        if (existing != null) {
            return buildExistingRefundResult(result, existing);
        }
        if (!"PAID".equals(parent.getOrderStatus()) || !"PAID".equals(parent.getPayStatus())) {
            return fail(result, "旅游票主订单未支付成功，不允许退款");
        }
        if (!StringUtils.hasText(parent.getPaymentOrderNo())) {
            paymentService.queryAndRefreshTravelPayResult(parent);
            parent = travelOrderMapper.selectByOrderNo(parentOrderNo);
        }
        if (parent == null || !StringUtils.hasText(parent.getPaymentOrderNo())) {
            return fail(result, "原支付订单号缺失，不允许退款");
        }

        List<DailyTicketOrder> children = orderMapper.selectByParentOrderNo(parentOrderNo);
        if (children == null || children.isEmpty()) {
            return fail(result, "旅游票子单不存在");
        }
        for (DailyTicketOrder child : children) {
            DailyTicketInstance ticket = instanceMapper.selectByOrderNo(child.getOrderNo());
            if (ticket != null && (TICKET_STATUS_USED.equals(ticket.getTicketStatus())
                    || TICKET_STATUS_EXPIRED.equals(ticket.getTicketStatus())
                    || TICKET_STATUS_REFUNDED.equals(ticket.getTicketStatus()))) {
                return fail(result, "旅游票存在已使用或已退款子单，不允许整单退款");
            }
            if (refundMapper.selectByOrderNo(child.getOrderNo()) != null) {
                return fail(result, "旅游票存在退款中的子单，不允许整单退款");
            }
        }

        List<DailyTicketInstance> locked = new ArrayList<>();
        for (DailyTicketOrder child : children) {
            DailyTicketInstance ticket = instanceMapper.selectByOrderNo(child.getOrderNo());
            if (ticket != null) {
                if (!TICKET_STATUS_ACTIVATED.equals(ticket.getTicketStatus())
                        || !lockTicketForRefund(ticket)) {
                    for (DailyTicketInstance rollback : locked) {
                        releaseTicketLock(rollback.getOrderNo());
                    }
                    return fail(result, "旅游票子单状态已变更，不允许整单退款");
                }
                locked.add(ticket);
            }
        }

        DailyTicketRefund refund = buildTravelRefund(parent, "TRAVEL_FULL", parent.getTotalAmount());
        try {
            refundMapper.insert(refund);
        } catch (RuntimeException e) {
            if (!isDuplicateRefund(e)) {
                throw e;
            }
            DailyTicketRefund existing2 = refundMapper.selectByOrderNo(parentOrderNo);
            if (existing2 != null) {
                log.warn("旅游票整单退款并发命中唯一索引，回查已有退款单收口 parentOrderNo={}", parentOrderNo);
                return buildExistingRefundResult(result, existing2);
            }
            throw e;
        }
        insertTravelRefundDetails(refund, parentOrderNo, children);
        travelOrderMapper.updateOrderStatus(parentOrderNo, "REFUNDING");
        if (isCxuhOrderSource(parent.getOrderSource())) {
            result.setRefundType("00");
            result.setOrderNo(refund.getRefundOrderNo());
            result.setRefundAmount(String.valueOf(refund.getRefundAmount()));
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc("退款已受理，等待小程序同步退款结果");
            return success(result);
        }

        Map<String, Object> refundRequest = buildTravelRefundRequest(parent, refund);
        DailyTicketPayGatewayResponse response;
        try {
            response = payGatewayClient.requestRefund(refundRequest);
        } catch (RuntimeException e) {
            insertPayLog(parentOrderNo, "REFUND", parent.getPayChannelCode(), refundRequest,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            result.setRefundType("00");
            result.setOrderNo(refund.getRefundOrderNo());
            result.setRefundAmount(String.valueOf(refund.getRefundAmount()));
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc("退款已提交，结果待确认");
            return success(result);
        }
        insertPayLog(parentOrderNo, "REFUND", parent.getPayChannelCode(), refundRequest, response);
        if (!isGatewaySuccess(response)) {
            travelOrderMapper.updateOrderStatus(parentOrderNo, "PAID");
            for (DailyTicketInstance rollback : locked) {
                releaseTicketLock(rollback.getOrderNo());
            }
            return fail(result, response == null ? "旅游票退款网关调用失败" : response.getMsg());
        }
        String refundTime = response.getData() == null ? null
                : stringValue(response.getData().get("refundTime"), null);
        if (StringUtils.hasText(refundTime)) {
            markTravelRefunded(parent, refund, response.getData());
        } else {
            updatePlatformRefundNo(refund, response.getData());
            markTravelRefunding(parentOrderNo, refund);
        }
        result.setRefundType("00");
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount()));
        result.setRefundResult(StringUtils.hasText(refundTime) ? "SUCCESS" : "PROCESSING");
        result.setRefundResultDesc(StringUtils.hasText(refundTime) ? "退款完成" : "退款申请已提交，请查询退款结果");
        return success(result);
    }

    /**
     * 判定异常是否为 {@code UK_DAILY_TICKET_REFUND_ORDER} 唯一索引冲突（同一订单已有退款单）。
     *
     * <p>实现已收口到 {@link DailyTicketRefundMessages#isDuplicateRefund(Throwable)} —— 取消单自动退款
     * 也要用同一份 cause 链判定，**NEVER 在这里复制一份**（本模块开了 tracing，观测切面会把异常重新包一层，
     * 两份实现一旦漂移就会有一处静默落空）。
     */
    private static boolean isDuplicateRefund(Throwable e) {
        return DailyTicketRefundMessages.isDuplicateRefund(e);
    }

    private DailyTicketRefund buildRefund(DailyTicketOrder order, String refundType) {
        Date now = new Date();
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setId(nextId());
        refund.setOrderNo(order.getOrderNo());
        refund.setOrderType(ORDER_TYPE_DAILY_TICKET);
        refund.setRefundOrderNo(buildRefundOrderNo(order.getOrderNo()));
        refund.setRefundAmount(order.getTicketPrice() == null ? 0 : order.getTicketPrice());
        refund.setRefundStatus("01".equals(refundType) ? "WAIT_VERIFY" : "REFUNDING");
        refund.setRefundType(refundType);
        refund.setVerifyAfterTime("01".equals(refundType) ? plusDays(now, 5) : null);
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        return refund;
    }

    private DailyTicketRefund buildTravelRefund(TravelTicketOrder parent, String scope, Integer amount) {
        Date now = new Date();
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setId(nextId());
        refund.setOrderNo(parent.getOrderNo());
        refund.setOrderType(ORDER_TYPE_TRAVEL_TICKET);
        refund.setRefundScope(scope);
        refund.setParentOrderNo(parent.getOrderNo());
        refund.setRefundReason("旅游票退款");
        refund.setRefundOrderNo(buildRefundOrderNo(parent.getOrderNo()));
        refund.setRefundAmount(amount == null ? 0 : amount);
        refund.setRefundStatus("REFUNDING");
        refund.setRefundType("00");
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        return refund;
    }

    private Date plusDays(Date base, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(base);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    private void markRefunded(DailyTicketOrder order, DailyTicketRefund refund, Map<String, Object> refundData) {
        refundSettlementService.markRefunded(order, refund, refundData);
    }

    private void markRefunding(DailyTicketOrder order, DailyTicketRefund refund) {
        refundSettlementService.markRefunding(order, refund);
    }

    private void updatePlatformRefundNo(DailyTicketRefund refund, Map<String, Object> refundData) {
        refundSettlementService.updatePlatformRefundNo(refund, refundData);
    }

    private void insertTravelRefundDetails(DailyTicketRefund refund, String parentOrderNo,
                                            List<DailyTicketOrder> children) {
        refundSettlementService.insertTravelRefundDetails(refund, parentOrderNo, children);
    }

    private Map<String, Object> buildTravelRefundRequest(TravelTicketOrder parent, DailyTicketRefund refund) {
        return refundGatewayRequests.buildTravelRefund(parent, refund);
    }

    private void markTravelRefunded(TravelTicketOrder parent, DailyTicketRefund refund,
                                    Map<String, Object> refundData) {
        refundSettlementService.markTravelRefunded(parent, refund, refundData);
    }

    private void markTravelRefunding(String parentOrderNo, DailyTicketRefund refund) {
        refundSettlementService.markTravelRefunding(parentOrderNo, refund);
    }

    private boolean lockTicketForRefund(DailyTicketInstance ticket) {
        return ticketLockWriter.lockForRefund(ticket);
    }

    private void releaseTicketLock(String orderNo) {
        ticketLockWriter.releaseLock(orderNo);
    }

    private void insertPayLog(String orderNo, String bizType, String payChannelCode, Object request, Object response) {
        payLogWriter.insert(orderNo, bizType, payChannelCode, request, response);
    }

    private Map<String, Object> buildDailyTicketRefundRequest(DailyTicketOrder order, DailyTicketRefund refund) {
        return refundGatewayRequests.buildDailyTicketRefund(order, refund);
    }

    private String buildRefundOrderNo(String orderNo) {
        return DailyTicketRefundMessages.buildRefundOrderNo(orderNo);
    }

    private DailyTicketRefundResult buildExistingRefundResult(DailyTicketRefundResult result,
                                                               DailyTicketRefund refund) {
        return DailyTicketRefundMessages.buildExistingRefundResult(result, refund);
    }

    private boolean isGatewaySuccess(DailyTicketPayGatewayResponse response) {
        return DailyTicketRefundMessages.isGatewaySuccess(response);
    }

    private String stringValue(Object value, String defaultValue) {
        return DailyTicketOrderSupport.stringValue(value, defaultValue);
    }

    private String nextId() {
        return DailyTicketOrderSupport.nextId();
    }

    private String validateOrderNo(String orderNo, String orderType) {
        return DailyTicketOrderSupport.validateOrderNo(orderNo, orderType);
    }

    private boolean isFreeOrder(DailyTicketOrder order) {
        return DailyTicketOrderSupport.isFreeOrder(order);
    }

    private boolean isFreeOrder(TravelTicketOrder order) {
        return DailyTicketOrderSupport.isFreeOrder(order);
    }

    private boolean isCxuhOrderSource(String orderSource) {
        return DailyTicketOrderSupport.isCxuhOrderSource(orderSource);
    }

    private <T extends DailyTicketBaseResult> T success(T result) {
        return DailyTicketOrderSupport.success(result);
    }

    private <T extends DailyTicketBaseResult> T fail(T result, String message) {
        return DailyTicketOrderSupport.fail(result, message);
    }
}
