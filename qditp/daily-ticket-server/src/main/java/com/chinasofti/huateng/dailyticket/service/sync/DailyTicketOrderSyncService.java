package com.chinasofti.huateng.dailyticket.service.sync;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.paylog.DailyTicketPayLogWriter;
import com.chinasofti.huateng.dailyticket.service.refund.DailyTicketRefundSettlementService;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketSyncOrderReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DailyTicketOrderSyncService {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketOrderSyncService.class);

    private static final String ORDER_TYPE_DAILY_TICKET = DailyTicketOrderSupport.ORDER_TYPE_DAILY_TICKET;
    private static final String ORDER_TYPE_TRAVEL_TICKET = DailyTicketOrderSupport.ORDER_TYPE_TRAVEL_TICKET;
    private static final String ORDER_SOURCE_CXUH = DailyTicketOrderSupport.ORDER_SOURCE_CXUH;
    private static final String CXUH_ORDER_PREFIX = DailyTicketOrderSupport.CXUH_ORDER_PREFIX;
    private static final String SYNC_EVENT_PAY_SUCCESS = "1";
    private static final String SYNC_EVENT_REFUND_SUCCESS = "2";

    private final DailyTicketOrderMapper orderMapper;
    private final TravelTicketOrderMapper travelOrderMapper;
    private final DailyTicketRefundMapper refundMapper;
    private final DailyTicketRefundSettlementService refundSettlementService;
    private final DailyTicketPayLogWriter payLogWriter;

    public DailyTicketOrderSyncService(DailyTicketOrderMapper orderMapper,
                                       TravelTicketOrderMapper travelOrderMapper,
                                       DailyTicketRefundMapper refundMapper,
                                       DailyTicketRefundSettlementService refundSettlementService,
                                       DailyTicketPayLogWriter payLogWriter) {
        this.orderMapper = orderMapper;
        this.travelOrderMapper = travelOrderMapper;
        this.refundMapper = refundMapper;
        this.refundSettlementService = refundSettlementService;
        this.payLogWriter = payLogWriter;
    }

    public DailyTicketBaseResult syncOrder(DailyTicketSyncOrderReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        String validMsg = validateSyncOrderRequest(request);
        if (validMsg != null) {
            return fail(result, validMsg);
        }
        if (ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return syncTravelOrder(request, result);
        }
        return syncDailyOrder(request, result);
    }

    private DailyTicketBaseResult syncDailyOrder(DailyTicketSyncOrderReqDTO request, DailyTicketBaseResult result) {
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        if (!isCxuhOrderSource(order.getOrderSource())) {
            return fail(result, "订单来源不允许小程序状态同步");
        }
        if (SYNC_EVENT_PAY_SUCCESS.equals(request.getEvent())) {
            syncDailyPaySuccess(order, request);
            success(result);
            insertPayLog(order.getOrderNo(), "CXUH_PAY_SYNC", request.getPayChannel(), request, result);
            return result;
        }
        DailyTicketRefund refund = refundMapper.selectByOrderNo(order.getOrderNo());
        if (refund == null) {
            return fail(result, "退款记录不存在");
        }
        if ("REFUNDED".equals(refund.getRefundStatus())) {
            return success(result);
        }
        if (!"REFUNDING".equals(refund.getRefundStatus()) && !"WAIT_VERIFY".equals(refund.getRefundStatus())) {
            return fail(result, "退款单状态不允许同步: " + refund.getRefundStatus());
        }
        markRefunded(order, refund, buildCxuhRefundData(request));
        success(result);
        insertPayLog(order.getOrderNo(), "CXUH_REFUND_SYNC", order.getPayChannelCode(), request, result);
        return result;
    }

    private DailyTicketBaseResult syncTravelOrder(DailyTicketSyncOrderReqDTO request, DailyTicketBaseResult result) {
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(request.getOrderNo());
        DailyTicketRefund refund = null;
        if (parent == null) {
            refund = refundMapper.selectByOrderNo(request.getOrderNo());
            if (refund == null || !ORDER_TYPE_TRAVEL_TICKET.equals(refund.getOrderType())) {
                return fail(result, "旅游票订单不存在");
            }
            parent = travelOrderMapper.selectByOrderNo(StringUtils.hasText(refund.getParentOrderNo())
                    ? refund.getParentOrderNo() : refund.getOrderNo());
        }
        if (parent == null) {
            return fail(result, "旅游票主订单不存在");
        }
        if (!isCxuhOrderSource(parent.getOrderSource())) {
            return fail(result, "订单来源不允许小程序状态同步");
        }
        if (SYNC_EVENT_PAY_SUCCESS.equals(request.getEvent())) {
            syncTravelPaySuccess(parent, request);
            success(result);
            insertPayLog(parent.getOrderNo(), "CXUH_PAY_SYNC", request.getPayChannel(), request, result);
            return result;
        }
        if (refund == null) {
            refund = refundMapper.selectByOrderNo(request.getOrderNo());
        }
        if (refund == null) {
            return fail(result, "退款记录不存在");
        }
        if ("REFUNDED".equals(refund.getRefundStatus())) {
            return success(result);
        }
        if (!"REFUNDING".equals(refund.getRefundStatus()) && !"WAIT_VERIFY".equals(refund.getRefundStatus())) {
            return fail(result, "退款单状态不允许同步: " + refund.getRefundStatus());
        }
        markTravelRefunded(parent, refund, buildCxuhRefundData(request));
        success(result);
        insertPayLog(refund.getOrderNo(), "CXUH_REFUND_SYNC", parent.getPayChannelCode(), request, result);
        return result;
    }

    private void syncDailyPaySuccess(DailyTicketOrder order, DailyTicketSyncOrderReqDTO request) {
        if ("PAID".equals(order.getOrderStatus()) && "PAID".equals(order.getPayStatus())) {
            return;
        }
        Date now = new Date();
        order.setOrderStatus("PAID");
        order.setPayStatus("PAID");
        order.setTradeNo(buildCxuhTradeNo(order.getOrderNo()));
        order.setPaymentOrderNo(buildCxuhTradeNo(order.getOrderNo()));
        order.setPayAmount(request.getPayAmount() == null ? order.getTicketPrice() : request.getPayAmount());
        order.setPayChannelCode(request.getPayChannel());
        order.setPayDate(request.getEventTime());
        order.setUpdateTime(now);
        if (orderMapper.updatePaySuccessByExternalSync(order) == 0) {
            DailyTicketOrder latest = orderMapper.selectByOrderNo(order.getOrderNo());
            if (latest == null || !"PAID".equals(latest.getOrderStatus()) || !"PAID".equals(latest.getPayStatus())) {
                log.warn("小程序支付同步未能推进日票订单 orderNo={}, currentOrderStatus={}, currentPayStatus={}",
                        order.getOrderNo(), latest == null ? null : latest.getOrderStatus(),
                        latest == null ? null : latest.getPayStatus());
            }
        }
    }

    private void syncTravelPaySuccess(TravelTicketOrder order, DailyTicketSyncOrderReqDTO request) {
        if ("PAID".equals(order.getOrderStatus()) && "PAID".equals(order.getPayStatus())) {
            return;
        }
        order.setOrderStatus("PAID");
        order.setPayStatus("PAID");
        order.setTradeNo(buildCxuhTradeNo(order.getOrderNo()));
        order.setPaymentOrderNo(buildCxuhTradeNo(order.getOrderNo()));
        order.setPayAmount(request.getPayAmount() == null ? order.getTotalAmount() : request.getPayAmount());
        order.setPayChannelCode(request.getPayChannel());
        order.setPayDate(request.getEventTime());
        order.setUpdateTime(new Date());
        if (travelOrderMapper.updatePaySuccessByExternalSync(order) == 0) {
            TravelTicketOrder latest = travelOrderMapper.selectByOrderNo(order.getOrderNo());
            if (latest == null || !"PAID".equals(latest.getOrderStatus()) || !"PAID".equals(latest.getPayStatus())) {
                log.warn("小程序支付同步未能推进旅游票主单 orderNo={}, currentOrderStatus={}, currentPayStatus={}",
                        order.getOrderNo(), latest == null ? null : latest.getOrderStatus(),
                        latest == null ? null : latest.getPayStatus());
            }
        }
    }

    private String validateSyncOrderRequest(DailyTicketSyncOrderReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (!ORDER_TYPE_DAILY_TICKET.equals(request.getOrderType())
                && !ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return "orderType必须为1或2";
        }
        if (!SYNC_EVENT_PAY_SUCCESS.equals(request.getEvent())
                && !SYNC_EVENT_REFUND_SUCCESS.equals(request.getEvent())) {
            return "event必须为1或2";
        }
        if (request.getEventTime() == null) {
            return "eventTime不能为空";
        }
        return null;
    }

    private boolean isCxuhOrderSource(String orderSource) {
        return DailyTicketOrderSupport.isCxuhOrderSource(orderSource);
    }

    private Map<String, Object> buildCxuhRefundData(DailyTicketSyncOrderReqDTO request) {
        Map<String, Object> refundData = new LinkedHashMap<>();
        refundData.put("refundNo", buildCxuhTradeNo(request.getOrderNo()));
        refundData.put("refundTime", new SimpleDateFormat("yyyyMMddHHmmss").format(request.getEventTime()));
        return refundData;
    }

    private String buildCxuhTradeNo(String orderNo) {
        return DailyTicketOrderSupport.buildCxuhTradeNo(orderNo);
    }

    private void insertPayLog(String orderNo, String bizType, String payChannelCode, Object request, Object response) {
        payLogWriter.insert(orderNo, bizType, payChannelCode, request, response);
    }

    private void markRefunded(DailyTicketOrder order, DailyTicketRefund refund, Map<String, Object> refundData) {
        refundSettlementService.markRefunded(order, refund, refundData);
    }

    private void markTravelRefunded(TravelTicketOrder parent, DailyTicketRefund refund,
                                    Map<String, Object> refundData) {
        refundSettlementService.markTravelRefunded(parent, refund, refundData);
    }

    private <T extends DailyTicketBaseResult> T success(T result) {
        return DailyTicketOrderSupport.success(result);
    }

    private <T extends DailyTicketBaseResult> T fail(T result, String message) {
        return DailyTicketOrderSupport.fail(result, message);
    }
}
