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
import com.chinasofti.huateng.dailyticket.service.paylog.DailyTicketPayLogWriter;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketInstanceStatus;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Map;


@Service
public class RefundProgressService {
    private static final Logger log = LoggerFactory.getLogger(RefundProgressService.class);

    private final DailyTicketOrderMapper orderMapper;
    private final TravelTicketOrderMapper travelOrderMapper;
    private final DailyTicketRefundMapper refundMapper;
    private final DailyTicketInstanceMapper instanceMapper;
    private final DailyTicketPayGatewayClient payGatewayClient;
    private final DailyTicketPayLogWriter payLogWriter;
    private final DailyTicketRefundSettlementService refundSettlementService;
    private final RefundGatewayRequests refundGatewayRequests;

    public RefundProgressService(DailyTicketOrderMapper orderMapper,
                                TravelTicketOrderMapper travelOrderMapper,
                                DailyTicketRefundMapper refundMapper,
                                DailyTicketInstanceMapper instanceMapper,
                                DailyTicketPayGatewayClient payGatewayClient,
                                DailyTicketPayLogWriter payLogWriter,
                                DailyTicketRefundSettlementService refundSettlementService,
                                RefundGatewayRequests refundGatewayRequests) {
        this.orderMapper = orderMapper;
        this.travelOrderMapper = travelOrderMapper;
        this.refundMapper = refundMapper;
        this.instanceMapper = instanceMapper;
        this.payGatewayClient = payGatewayClient;
        this.payLogWriter = payLogWriter;
        this.refundSettlementService = refundSettlementService;
        this.refundGatewayRequests = refundGatewayRequests;
    }

    public DailyTicketRefundResult queryRefundTicket(DailyTicketOrderNoReqDTO request) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        String validMsg = DailyTicketOrderSupport.validateOrderNo(request == null ? null : request.getOrderNo(),
                request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return DailyTicketOrderSupport.fail(result, validMsg);
        }
        if (DailyTicketOrderSupport.ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return queryTravelRefundTicket(request.getOrderNo());
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        DailyTicketRefund refund = refundMapper.selectByOrderNo(request.getOrderNo());
        if (order == null || refund == null) {
            return DailyTicketOrderSupport.fail(result, "退款记录不存在");
        }
        if (!"REFUNDING".equals(refund.getRefundStatus()) && !"FAILED".equals(refund.getRefundStatus())) {
            return DailyTicketRefundMessages.buildExistingRefundResult(result, refund);
        }

        refundSettlementService.restorePlatformRefundNoFromPayLog(order.getOrderNo(), refund);
        if (!StringUtils.hasText(refund.getPlatformRefundNo())) {
            return DailyTicketOrderSupport.fail(result, "支付平台退款单号缺失，无法执行双字段退款查询");
        }
        Map<String, Object> queryRequest = refundGatewayRequests.buildRefundQuery(refund);
        log.info("日票服务准备调用支付网关退款查询接口 orderNo={}, request={}",
                order.getOrderNo(), JSON.toJSONString(queryRequest));
        DailyTicketPayGatewayResponse queryResponse = payGatewayClient.requestRefundQuery(queryRequest);
        log.info("日票服务调用支付网关退款查询接口完成 orderNo={}, response={}",
                order.getOrderNo(), JSON.toJSONString(queryResponse));
        payLogWriter.insert(order.getOrderNo(), "REFUND_QUERY", order.getPayChannelCode(), queryRequest, queryResponse);
        if (!DailyTicketRefundMessages.isGatewaySuccess(queryResponse) || queryResponse.getData() == null) {
            return DailyTicketOrderSupport.fail(result,
                    queryResponse == null ? "退款结果查询失败" : queryResponse.getMsg());
        }

        refundSettlementService.persistPlatformRefundNoIfChanged(refund, queryResponse.getData());
        String status = DailyTicketOrderSupport.stringValue(queryResponse.getData().get("status"), null);
        if (isRefundSuccessStatus(status)) {
            refundSettlementService.markRefunded(order, refund, queryResponse.getData());
            return DailyTicketRefundMessages.buildExistingRefundResult(result, refund);
        }
        if (isRefundFailedStatus(status)) {
            refundSettlementService.markRefundFailed(order, refund, queryResponse.getData());
            result.setRefundType(refund.getRefundType());
            result.setOrderNo(refund.getRefundOrderNo());
            result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
            result.setRefundResult("FAILED");
            result.setRefundResultDesc("支付平台退款失败，可发起退款重试");
            return DailyTicketOrderSupport.success(result);
        }

        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundResult("PROCESSING");
        result.setRefundResultDesc("支付平台退款处理中");
        return DailyTicketOrderSupport.success(result);
    }

    public DailyTicketRefundResult queryTravelRefundTicket(String orderNo) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        DailyTicketRefund refund = refundMapper.selectByOrderNo(orderNo);
        if (refund == null) {
            return DailyTicketOrderSupport.fail(result, "退款记录不存在");
        }
        String parentOrderNo = StringUtils.hasText(refund.getParentOrderNo())
                ? refund.getParentOrderNo() : refund.getOrderNo();
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(parentOrderNo);
        if (parent == null) {
            return DailyTicketOrderSupport.fail(result, "旅游票主订单不存在");
        }
        if (!"REFUNDING".equals(refund.getRefundStatus()) && !"FAILED".equals(refund.getRefundStatus())) {
            return DailyTicketRefundMessages.buildExistingRefundResult(result, refund);
        }
        refundSettlementService.restorePlatformRefundNoFromPayLog(refund.getOrderNo(), refund);
        if (!StringUtils.hasText(refund.getPlatformRefundNo())) {
            return DailyTicketOrderSupport.fail(result, "支付平台退款单号缺失，无法执行退款查询");
        }
        Map<String, Object> queryRequest = refundGatewayRequests.buildRefundQuery(refund);
        DailyTicketPayGatewayResponse response = payGatewayClient.requestRefundQuery(queryRequest);
        payLogWriter.insert(refund.getOrderNo(), "REFUND_QUERY", parent.getPayChannelCode(), queryRequest, response);
        if (!DailyTicketRefundMessages.isGatewaySuccess(response) || response.getData() == null) {
            return DailyTicketOrderSupport.fail(result, response == null ? "退款结果查询失败" : response.getMsg());
        }
        refundSettlementService.persistPlatformRefundNoIfChanged(refund, response.getData());
        String status = DailyTicketOrderSupport.stringValue(response.getData().get("status"), null);
        if (isRefundSuccessStatus(status)) {
            refundSettlementService.markTravelRefunded(parent, refund, response.getData());
            return DailyTicketRefundMessages.buildExistingRefundResult(result, refund);
        }
        if (isRefundFailedStatus(status)) {
            refundSettlementService.markTravelRefundFailed(parent, refund, response.getData());
            return DailyTicketRefundMessages.buildExistingRefundResult(result, refund);
        }
        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundResult("PROCESSING");
        result.setRefundResultDesc("支付平台退款处理中");
        return DailyTicketOrderSupport.success(result);
    }

    public DailyTicketRefundResult retryRefundTicket(DailyTicketOrderNoReqDTO request) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        String validMsg = DailyTicketOrderSupport.validateOrderNo(request == null ? null : request.getOrderNo(),
                request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return DailyTicketOrderSupport.fail(result, validMsg);
        }
        if (DailyTicketOrderSupport.ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return retryTravelRefundTicket(request.getOrderNo());
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        DailyTicketRefund refund = refundMapper.selectByOrderNo(request.getOrderNo());
        if (order == null || refund == null) {
            return DailyTicketOrderSupport.fail(result, "退款记录不存在");
        }
        if (!"00".equals(refund.getRefundType())) {
            return DailyTicketOrderSupport.fail(result, "核验退款不支持支付平台重试");
        }
        if (!"REFUNDING".equals(refund.getRefundStatus()) && !"FAILED".equals(refund.getRefundStatus())) {
            return DailyTicketRefundMessages.buildExistingRefundResult(result, refund);
        }

        DailyTicketRefundResult queryResult = queryRefundTicket(request);
        if (!DailyTicketOrderSupport.RET_SUCCESS.equals(queryResult.getRetCode())
                || (!"PROCESSING".equals(queryResult.getRefundResult())
                && !"FAILED".equals(queryResult.getRefundResult()))) {
            return queryResult;
        }

        Map<String, Object> refundRequest = refundGatewayRequests.buildDailyTicketRefund(order, refund);
        log.info("日票服务准备重试支付网关退款接口 orderNo={}, request={}",
                order.getOrderNo(), JSON.toJSONString(refundRequest));
        DailyTicketPayGatewayResponse retryResponse = payGatewayClient.requestRefund(refundRequest);
        log.info("日票服务重试支付网关退款接口完成 orderNo={}, response={}",
                order.getOrderNo(), JSON.toJSONString(retryResponse));
        payLogWriter.insert(order.getOrderNo(), "REFUND_RETRY", order.getPayChannelCode(), refundRequest, retryResponse);
        if (!DailyTicketRefundMessages.isGatewaySuccess(retryResponse)) {
            return DailyTicketOrderSupport.fail(result,
                    retryResponse == null ? "退款重试调用失败" : retryResponse.getMsg());
        }

        String refundTime = retryResponse.getData() == null ? null
                : DailyTicketOrderSupport.stringValue(retryResponse.getData().get("refundTime"), null);
        if (StringUtils.hasText(refundTime)) {
            refundSettlementService.markRefunded(order, refund, retryResponse.getData());
            return DailyTicketRefundMessages.buildExistingRefundResult(result, refund);
        }
        refundSettlementService.updatePlatformRefundNo(refund, retryResponse.getData());
        refundSettlementService.markRefunding(order, refund);
        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundResult("PROCESSING");
        result.setRefundResultDesc("退款重试已提交，请查询退款结果");
        return DailyTicketOrderSupport.success(result);
    }

    public DailyTicketRefundResult retryTravelRefundTicket(String orderNo) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        DailyTicketRefund refund = refundMapper.selectByOrderNo(orderNo);
        if (refund == null) {
            return DailyTicketOrderSupport.fail(result, "退款记录不存在");
        }
        String parentOrderNo = StringUtils.hasText(refund.getParentOrderNo())
                ? refund.getParentOrderNo() : refund.getOrderNo();
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(parentOrderNo);
        if (parent == null || !StringUtils.hasText(parent.getPaymentOrderNo())) {
            return DailyTicketOrderSupport.fail(result, "旅游票原支付订单号缺失，不允许退款重试");
        }
        if (!"REFUNDING".equals(refund.getRefundStatus()) && !"FAILED".equals(refund.getRefundStatus())) {
            return DailyTicketRefundMessages.buildExistingRefundResult(result, refund);
        }
        DailyTicketRefundResult queryResult = queryTravelRefundTicket(orderNo);
        if (!DailyTicketOrderSupport.RET_SUCCESS.equals(queryResult.getRetCode())
                || (!"PROCESSING".equals(queryResult.getRefundResult())
                && !"FAILED".equals(queryResult.getRefundResult()))) {
            return queryResult;
        }
        Map<String, Object> retryRequest = "TRAVEL_SUB".equals(refund.getRefundScope())
                ? refundGatewayRequests.buildTravelSubRefund(parent, refund)
                : refundGatewayRequests.buildTravelRefund(parent, refund);
        DailyTicketPayGatewayResponse response = payGatewayClient.requestRefund(retryRequest);
        payLogWriter.insert(refund.getOrderNo(), "REFUND_RETRY", parent.getPayChannelCode(), retryRequest, response);
        if (!DailyTicketRefundMessages.isGatewaySuccess(response)) {
            return DailyTicketOrderSupport.fail(result, response == null ? "退款重试调用失败" : response.getMsg());
        }
        String refundTime = response.getData() == null ? null
                : DailyTicketOrderSupport.stringValue(response.getData().get("refundTime"), null);
        if (StringUtils.hasText(refundTime)) {
            refundSettlementService.markTravelRefunded(parent, refund, response.getData());
            return DailyTicketRefundMessages.buildExistingRefundResult(result, refund);
        }
        refundSettlementService.updatePlatformRefundNo(refund, response.getData());
        refund.setRefundStatus("REFUNDING");
        refund.setRefundDate(null);
        refund.setUpdateTime(new Date());
        refundMapper.updateResult(refund);
        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundResult("PROCESSING");
        result.setRefundResultDesc("退款重试已提交，请查询退款结果");
        return DailyTicketOrderSupport.success(result);
    }


    public DailyTicketRefundResult resubmitRefundTicket(DailyTicketOrderNoReqDTO request) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        String validMsg = DailyTicketOrderSupport.validateOrderNo(request == null ? null : request.getOrderNo(),
                request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return DailyTicketOrderSupport.fail(result, validMsg);
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        DailyTicketRefund refund = refundMapper.selectByOrderNo(request.getOrderNo());
        if (order == null || refund == null) {
            return DailyTicketOrderSupport.fail(result, "退款记录不存在");
        }
        String refundStatus = refund.getRefundStatus();
        if (!"REFUNDING".equals(refundStatus) && !"FAILED".equals(refundStatus)
                && !"WAIT_VERIFY".equals(refundStatus)) {
            return DailyTicketRefundMessages.buildExistingRefundResult(result, refund);
        }
        if ("WAIT_VERIFY".equals(refundStatus)
                && refund.getVerifyAfterTime() != null
                && refund.getVerifyAfterTime().after(new Date())) {
            return DailyTicketOrderSupport.fail(result, "核验退款观察期未满，不允许重提交");
        }
        if ("01".equals(refund.getRefundType())) {
            DailyTicketInstance ticket = instanceMapper.selectByOrderNo(order.getOrderNo());
            if (ticket == null || !DailyTicketInstanceStatus.REFUND_LOCKED.equals(ticket.getTicketStatus())) {
                log.warn("日票核验退款重提交：票不在 REFUND_LOCKED，拒绝放款 orderNo={}, ticketStatus={}",
                        order.getOrderNo(), ticket == null ? null : ticket.getTicketStatus());
                return DailyTicketOrderSupport.fail(result, "车票状态已变更，不允许放款，请人工核验");
            }
        }
        refundSettlementService.restorePlatformRefundNoFromPayLog(order.getOrderNo(), refund);
        if (StringUtils.hasText(refund.getPlatformRefundNo())) {
            return DailyTicketOrderSupport.fail(result, "支付平台已受理该退款单，请走退款结果查询或退款重试");
        }
        if (!StringUtils.hasText(order.getPaymentOrderNo())) {
            return DailyTicketOrderSupport.fail(result, "原支付订单号缺失，不允许退款");
        }

        Map<String, Object> resubmitRequest = refundGatewayRequests.buildDailyTicketRefund(order, refund);
        log.info("日票服务准备重提交支付网关退款接口 orderNo={}, refundOrderNo={}, refundStatus={}, request={}",
                order.getOrderNo(), refund.getRefundOrderNo(), refundStatus, JSON.toJSONString(resubmitRequest));
        DailyTicketPayGatewayResponse resubmitResponse = payGatewayClient.requestRefund(resubmitRequest);
        log.info("日票服务重提交支付网关退款接口完成 orderNo={}, response={}",
                order.getOrderNo(), JSON.toJSONString(resubmitResponse));
        payLogWriter.insert(order.getOrderNo(), "REFUND_RESUBMIT", order.getPayChannelCode(),
                resubmitRequest, resubmitResponse);
        if (!DailyTicketRefundMessages.isGatewaySuccess(resubmitResponse)) {
            return DailyTicketOrderSupport.fail(result,
                    resubmitResponse == null ? "退款重提交调用失败" : resubmitResponse.getMsg());
        }

        String refundTime = resubmitResponse.getData() == null ? null
                : DailyTicketOrderSupport.stringValue(resubmitResponse.getData().get("refundTime"), null);
        if (StringUtils.hasText(refundTime)) {
            refundSettlementService.markRefunded(order, refund, resubmitResponse.getData());
            return DailyTicketRefundMessages.buildExistingRefundResult(result, refund);
        }
        refundSettlementService.updatePlatformRefundNo(refund, resubmitResponse.getData());
        refundSettlementService.markRefunding(order, refund);
        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundResult("PROCESSING");
        result.setRefundResultDesc("退款重提交已提交，请查询退款结果");
        return DailyTicketOrderSupport.success(result);
    }

    private boolean isRefundSuccessStatus(String status) {
        return "SUCCESS".equalsIgnoreCase(status) || "REFUNDED".equalsIgnoreCase(status)
                || "REFUND_SUCCESS".equalsIgnoreCase(status) || "1".equals(status);
    }

    private boolean isRefundFailedStatus(String status) {
        return "FAIL".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)
                || "REFUND_FAILED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)
                || "CANCELED".equalsIgnoreCase(status) || "0".equals(status);
    }
}
