package com.chinasofti.huateng.dailyticket.service.refund;

import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayClient;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundDetailMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.page.TravelTicketSubRefundRequest;
import com.chinasofti.huateng.dailyticket.service.paylog.DailyTicketPayLogWriter;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketInstanceStatus;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 旅游票**子单**退款发起：运营后台按单张子票退款的唯一实现。
 *
 * <p>与「整单退款」（仍在 {@code DailyTicketServiceImpl.requestTravelRefund}）是两条链路、**NEVER 合并**：
 * 整单退一次性覆盖全部子单并把主单置 {@code REFUNDED}，子单退只动一张票、主单状态交给
 * {@code TravelParentSummaryWriter} 按剩余子单重算。
 *
 * <p><b>零 {@code @Transactional}、NEVER 加</b>：本方法中段要调支付中心网关，
 * 加事务即同时踩 AGENTS.md §5.2「事务内 NEVER 发 RPC」与「事务内 NEVER 发通知」
 * （收口服务的终态入口会 {@code deliverOne} 推 APP）。
 *
 * <p>六道前置校验的顺序**逐字保留**、NEVER 重排：主子关系 → 免费单 → 子单已有退款（幂等返成功）
 * → 主单已有整单退款且非 {@code FAILED} → 主单支付态白名单 → 原支付订单号 → 票状态 → 抢票锁。
 */
@Service
public class TravelSubRefundService {

    private final TravelTicketOrderMapper travelOrderMapper;
    private final DailyTicketOrderMapper orderMapper;
    private final DailyTicketRefundMapper refundMapper;
    private final DailyTicketRefundDetailMapper refundDetailMapper;
    private final DailyTicketInstanceMapper instanceMapper;
    private final DailyTicketPayGatewayClient payGatewayClient;
    private final DailyTicketPayLogWriter payLogWriter;
    private final DailyTicketTicketLockWriter ticketLockWriter;
    private final DailyTicketRefundSettlementService refundSettlementService;
    private final RefundGatewayRequests refundGatewayRequests;

    public TravelSubRefundService(TravelTicketOrderMapper travelOrderMapper,
                                 DailyTicketOrderMapper orderMapper,
                                 DailyTicketRefundMapper refundMapper,
                                 DailyTicketRefundDetailMapper refundDetailMapper,
                                 DailyTicketInstanceMapper instanceMapper,
                                 DailyTicketPayGatewayClient payGatewayClient,
                                 DailyTicketPayLogWriter payLogWriter,
                                 DailyTicketTicketLockWriter ticketLockWriter,
                                 DailyTicketRefundSettlementService refundSettlementService,
                                 RefundGatewayRequests refundGatewayRequests) {
        this.travelOrderMapper = travelOrderMapper;
        this.orderMapper = orderMapper;
        this.refundMapper = refundMapper;
        this.refundDetailMapper = refundDetailMapper;
        this.instanceMapper = instanceMapper;
        this.payGatewayClient = payGatewayClient;
        this.payLogWriter = payLogWriter;
        this.ticketLockWriter = ticketLockWriter;
        this.refundSettlementService = refundSettlementService;
        this.refundGatewayRequests = refundGatewayRequests;
    }

    public DailyTicketRefundResult requestTravelSubRefund(TravelTicketSubRefundRequest request) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        if (request == null || !StringUtils.hasText(request.getParentOrderNo())) {
            return DailyTicketOrderSupport.fail(result, "旅游票主单号不能为空");
        }
        if (!StringUtils.hasText(request.getSubOrderNo())) {
            return DailyTicketOrderSupport.fail(result, "旅游票子单号不能为空");
        }
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(request.getParentOrderNo());
        DailyTicketOrder child = orderMapper.selectByOrderNo(request.getSubOrderNo());
        if (parent == null || child == null || !request.getParentOrderNo().equals(child.getParentOrderNo())) {
            return DailyTicketOrderSupport.fail(result, "旅游票主子单关系不存在");
        }
        if (DailyTicketOrderSupport.isFreeOrder(parent)) {
            return DailyTicketOrderSupport.fail(result, "免费订单不支持退款");
        }
        DailyTicketRefund existing = refundMapper.selectByOrderNo(child.getOrderNo());
        if (existing != null) {
            return DailyTicketRefundMessages.buildExistingRefundResult(result, existing);
        }
        DailyTicketRefund parentRefund = refundMapper.selectByOrderNo(parent.getOrderNo());
        if (parentRefund != null && !"FAILED".equals(parentRefund.getRefundStatus())) {
            return DailyTicketOrderSupport.fail(result, "旅游票主单已存在整单退款，不允许子单退款");
        }
        if (!"PAID".equals(parent.getPayStatus())
                || (!"PAID".equals(parent.getOrderStatus()) && !"PARTIAL_USED".equals(parent.getOrderStatus())
                && !"PARTIAL_REFUNDED".equals(parent.getOrderStatus()))) {
            return DailyTicketOrderSupport.fail(result, "旅游票主单未支付成功，不允许子单退款");
        }
        if (!StringUtils.hasText(parent.getPaymentOrderNo())) {
            return DailyTicketOrderSupport.fail(result, "原支付订单号缺失，不允许退款");
        }
        DailyTicketInstance ticket = instanceMapper.selectByOrderNo(child.getOrderNo());
        if (ticket != null && !DailyTicketInstanceStatus.ACTIVATED.equals(ticket.getTicketStatus())) {
            return DailyTicketOrderSupport.fail(result, "旅游票子单已使用或已退款，不允许退款");
        }
        if (ticket != null && !ticketLockWriter.lockForRefund(ticket)) {
            return DailyTicketOrderSupport.fail(result, "旅游票子单状态已变更，不允许退款");
        }

        DailyTicketRefund refund = buildTravelSubRefund(parent, child, request);
        refundMapper.insert(refund);
        List<DailyTicketOrder> details = new ArrayList<>();
        details.add(child);
        refundSettlementService.insertTravelRefundDetails(refund, parent.getOrderNo(), details);
        if (DailyTicketOrderSupport.isCxuhOrderSource(parent.getOrderSource())) {
            result.setRefundType("00");
            result.setOrderNo(refund.getRefundOrderNo());
            result.setRefundAmount(String.valueOf(refund.getRefundAmount()));
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc("退款已受理，等待小程序同步退款结果");
            return DailyTicketOrderSupport.success(result);
        }

        Map<String, Object> refundRequest = buildTravelSubRefundRequest(parent, refund);
        DailyTicketPayGatewayResponse response;
        try {
            response = payGatewayClient.requestRefund(refundRequest);
        } catch (RuntimeException e) {
            payLogWriter.insert(child.getOrderNo(), "REFUND", parent.getPayChannelCode(), refundRequest,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            result.setRefundType("00");
            result.setOrderNo(refund.getRefundOrderNo());
            result.setRefundAmount(String.valueOf(refund.getRefundAmount()));
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc("退款已提交，结果待确认");
            return DailyTicketOrderSupport.success(result);
        }
        payLogWriter.insert(child.getOrderNo(), "REFUND", parent.getPayChannelCode(), refundRequest, response);
        if (!DailyTicketRefundMessages.isGatewaySuccess(response)) {
            refund.setRefundStatus("FAILED");
            refund.setUpdateTime(new Date());
            refundMapper.updateResult(refund);
            refundDetailMapper.updateStatusByRefundOrderNo(refund.getRefundOrderNo(), "FAILED");
            ticketLockWriter.releaseLock(child.getOrderNo());
            return DailyTicketOrderSupport.fail(result,
                    response == null ? "旅游票子单退款网关调用失败" : response.getMsg());
        }
        String refundTime = response.getData() == null ? null
                : DailyTicketOrderSupport.stringValue(response.getData().get("refundTime"), null);
        if (StringUtils.hasText(refundTime)) {
            refundSettlementService.markTravelRefunded(parent, refund, response.getData());
        } else {
            refundSettlementService.updatePlatformRefundNo(refund, response.getData());
            refund.setUpdateTime(new Date());
            refundMapper.updateResult(refund);
        }
        result.setRefundType("00");
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount()));
        result.setRefundResult(StringUtils.hasText(refundTime) ? "SUCCESS" : "PROCESSING");
        result.setRefundResultDesc(StringUtils.hasText(refundTime) ? "退款完成" : "退款申请已提交，请查询退款结果");
        return DailyTicketOrderSupport.success(result);
    }

    /**
     * 子单退款单：{@code ORDER_NO} 记**子单号**、{@code PARENT_ORDER_NO} 记主单号、
     * {@code REFUND_SCOPE='TRAVEL_SUB'}，金额取子单票价。
     * 收口服务靠 {@code REFUND_SCOPE} 区分整单 / 子单两套推进逻辑，**这三项 NEVER 改**。
     */
    private DailyTicketRefund buildTravelSubRefund(TravelTicketOrder parent, DailyTicketOrder child,
                                                   TravelTicketSubRefundRequest request) {
        Date now = new Date();
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setId(DailyTicketOrderSupport.nextId());
        refund.setOrderNo(child.getOrderNo());
        refund.setOrderType(DailyTicketOrderSupport.ORDER_TYPE_TRAVEL_TICKET);
        refund.setRefundScope("TRAVEL_SUB");
        refund.setParentOrderNo(parent.getOrderNo());
        refund.setRefundReason(StringUtils.hasText(request.getRefundReason())
                ? request.getRefundReason() : "旅游票子单退款");
        refund.setOperator(request.getOperator());
        refund.setRefundOrderNo(DailyTicketRefundMessages.buildRefundOrderNo(child.getOrderNo()));
        refund.setRefundAmount(child.getTicketPrice() == null ? 0 : child.getTicketPrice());
        refund.setRefundStatus("REFUNDING");
        refund.setRefundType("00");
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        return refund;
    }

    /**
     * 送支付中心的子单退款报文，组装收口在 {@link RefundGatewayRequests#buildTravelSubRefund}
     * （同一份报文「发起」与「重试」两处共用，**NEVER 在本类里再写一份**）。
     */
    public Map<String, Object> buildTravelSubRefundRequest(TravelTicketOrder parent, DailyTicketRefund refund) {
        return refundGatewayRequests.buildTravelSubRefund(parent, refund);
    }
}
