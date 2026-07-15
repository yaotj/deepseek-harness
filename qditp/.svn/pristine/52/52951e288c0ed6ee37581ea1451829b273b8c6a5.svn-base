package com.chinasofti.huateng.dailyticket.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayClient;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.config.DailyTicketPayProperties;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketPayLogMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketPayLog;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.service.DailyTicketService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketActivateReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 日票业务服务默认实现。
 */
@Service
public class DailyTicketServiceImpl implements DailyTicketService {
    private static final String RET_SUCCESS = "0000";
    private static final String RET_FAIL = "9999";
    private static final String ORDER_TYPE_DAILY_TICKET = "1";
    private static final String CODE_TICKET_TYPE = "0441";

    private final AtomicInteger orderSequence = new AtomicInteger(1);
    private final DailyTicketPayGatewayClient payGatewayClient;
    private final DailyTicketPayProperties payProperties;
    private final DailyTicketOrderMapper orderMapper;
    private final DailyTicketInstanceMapper instanceMapper;
    private final DailyTicketPayLogMapper payLogMapper;
    private final DailyTicketRefundMapper refundMapper;

    public DailyTicketServiceImpl(DailyTicketPayGatewayClient payGatewayClient,
                                  DailyTicketPayProperties payProperties,
                                  DailyTicketOrderMapper orderMapper,
                                  DailyTicketInstanceMapper instanceMapper,
                                  DailyTicketPayLogMapper payLogMapper,
                                  DailyTicketRefundMapper refundMapper) {
        this.payGatewayClient = payGatewayClient;
        this.payProperties = payProperties;
        this.orderMapper = orderMapper;
        this.instanceMapper = instanceMapper;
        this.payLogMapper = payLogMapper;
        this.refundMapper = refundMapper;
    }

    @Override
    public DailyTicketOrderResult requestCountingOrder(DailyTicketOrderReqDTO request) {
        DailyTicketOrderResult result = new DailyTicketOrderResult();
        String validMsg = validateOrderRequest(request);
        if (validMsg != null) {
            return fail(result, validMsg);
        }

        Date now = new Date();
        DailyTicketOrder order = new DailyTicketOrder();
        order.setId(nextId());
        order.setOrderNo(nextOrderNo());
        order.setOrderType(ORDER_TYPE_DAILY_TICKET);
        order.setOrderSource(request.getOrderSource());
        order.setTicketPrice(request.getTicketPrice());
        order.setCardType(request.getCardType());
        order.setShowType(request.getShowType());
        order.setUserId(request.getUserId());
        order.setOrderStatus("CREATED");
        order.setPayStatus("INIT");
        order.setCreateTime(now);
        order.setUpdateTime(now);
        orderMapper.insert(order);

        success(result);
        result.setOrderNo(order.getOrderNo());
        return result;
    }

    @Override
    public DailyTicketPayResult requestPay(DailyTicketPayReqDTO request) {
        DailyTicketPayResult result = new DailyTicketPayResult();
        String validMsg = validateOrderNo(request == null ? null : request.getOrderNo(), request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return fail(result, validMsg);
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        if (!"CREATED".equals(order.getOrderStatus()) && !"PAYING".equals(order.getOrderStatus())) {
            return fail(result, "订单状态不允许支付");
        }

        Date now = new Date();
        order.setThirdUserId(request.getThirdUserId());
        order.setPayChannelCode(request.getPayChannelCode());
        order.setChannelType(request.getChannelType());
        order.setPayScene(resolveScene(request.getChannelType()));
        order.setOrderStatus("PAYING");
        order.setPayStatus("PAYING");
        order.setUpdateTime(now);
        orderMapper.updatePayRequest(order);

        Map<String, Object> payRequest = buildDailyTicketPayRequest(order, request);
        DailyTicketPayGatewayResponse payResponse = payGatewayClient.requestPay(payRequest);
        insertPayLog(order.getOrderNo(), "PAY", request.getPayChannelCode(), payRequest, payResponse);
        if (!isGatewaySuccess(payResponse)) {
            order.setOrderStatus("PAY_FAILED");
            order.setPayStatus("FAIL");
            order.setUpdateTime(new Date());
            orderMapper.updatePayResult(order);
            return fail(result, payResponse == null ? "日票支付网关调用失败" : payResponse.getMsg());
        }

        success(result);
        result.setSignType("01");
        result.setSign("");
        result.setPayChannelCode(request.getPayChannelCode());
        result.setPaymentInfo(stringValue(payResponse.getData() == null ? null : payResponse.getData().get("data"), null));
        result.setDiscountInfo(null);
        return result;
    }

    @Override
    public DailyTicketPayQueryResult requestPayResult(DailyTicketOrderNoReqDTO request) {
        DailyTicketPayQueryResult result = new DailyTicketPayQueryResult();
        String validMsg = validateOrderNo(request == null ? null : request.getOrderNo(), request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return fail(result, validMsg);
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }

        success(result);
        result.setTradeNo(order.getTradeNo());
        result.setPayResult("PAID".equals(order.getPayStatus()) ? "success" : "processing");
        result.setPayAmount(order.getPayAmount() == null ? order.getTicketPrice() : order.getPayAmount());
        result.setPayDate(order.getPayDate());
        result.setDiscountInfo(null);
        result.setPayChannel(order.getPayChannelCode());
        result.setChannelDiscount(0);
        result.setCouponDiscount(0);
        return result;
    }

    @Override
    public DailyTicketRefundResult requestRefundTicket(DailyTicketOrderNoReqDTO request) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        String validMsg = validateOrderNo(request == null ? null : request.getOrderNo(), request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return fail(result, validMsg);
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }

        DailyTicketInstance ticket = instanceMapper.selectByOrderNo(order.getOrderNo());
        if (ticket != null && "USED".equals(ticket.getTicketStatus())) {
            return fail(result, "车票已使用，不允许退款");
        }

        String refundType = ticket == null || !"ACTIVATED".equals(ticket.getTicketStatus()) ? "00" : "01";
        DailyTicketRefund refund = buildRefund(order, refundType);
        refundMapper.insert(refund);

        if ("01".equals(refundType)) {
            orderMapper.updateOrderStatus(order.getOrderNo(), "REFUNDING");
            result.setRefundType(refundType);
            result.setOrderNo(order.getOrderNo());
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc("已激活车票进入5天核验退款流程");
            result.setRefundAmount(String.valueOf(refund.getRefundAmount()));
            return success(result);
        }

        Map<String, Object> refundReq = buildDailyTicketRefundRequest(order, refund);
        DailyTicketPayGatewayResponse refundResponse = payGatewayClient.requestRefund(refundReq);
        insertPayLog(order.getOrderNo(), "REFUND", order.getPayChannelCode(), refundReq, refundResponse);
        if (!isGatewaySuccess(refundResponse)) {
            orderMapper.updateOrderStatus(order.getOrderNo(), "REFUNDING");
            return fail(result, refundResponse == null ? "日票退款网关调用失败" : refundResponse.getMsg());
        }

        Date now = new Date();
        refund.setRefundOrderNo(stringValue(refundResponse.getData() == null ? null : refundResponse.getData().get("refundOrderNo"), refund.getId()));
        refund.setRefundStatus("REFUNDED");
        refund.setRefundDate(now);
        refund.setUpdateTime(now);
        refundMapper.updateResult(refund);
        orderMapper.updateOrderStatus(order.getOrderNo(), "REFUNDED");

        result.setRefundType(refundType);
        result.setOrderNo(refund.getRefundOrderNo() == null ? order.getOrderNo() : refund.getRefundOrderNo());
        result.setRefundResultDesc("退款");
        result.setRefundResult("SUCCESS");
        result.setRefundDate(stringValue(refundResponse.getData() == null ? null : refundResponse.getData().get("refundTime"), new SimpleDateFormat("yyyyMMddHHmmss").format(now)));
        result.setRefundAmount(String.valueOf(order.getTicketPrice() == null ? 0 : order.getTicketPrice()));
        return success(result);
    }

    @Override
    public DailyTicketBaseResult cancelOrder(DailyTicketOrderNoReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        String validMsg = validateOrderNo(request == null ? null : request.getOrderNo(), request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return fail(result, validMsg);
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        if (!"CREATED".equals(order.getOrderStatus())) {
            return fail(result, "订单状态不允许取消");
        }
        orderMapper.updateOrderStatus(order.getOrderNo(), "CANCELED");
        return success(result);
    }

    @Override
    public DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return fail(result, "orderNo不能为空");
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        if (!"PAID".equals(order.getOrderStatus())) {
            return fail(result, "订单未支付，不能激活");
        }

        Date now = new Date();
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setId(nextId());
        ticket.setOrderNo(request.getOrderNo());
        ticket.setThirdUserId(request.getThirdUserId());
        ticket.setCardNum(request.getCardNum());
        ticket.setCardIssue(request.getCardIssue());
        ticket.setAppCardType(order.getCardType());
        ticket.setCodeTicketType(CODE_TICKET_TYPE);
        ticket.setTicketType(request.getTicketType());
        ticket.setShowType(request.getShowType());
        ticket.setTicketCode(request.getTicketCode());
        ticket.setTicketName(request.getTicketName());
        ticket.setPeriod(request.getPeriod());
        ticket.setActualTimes(request.getActualTimes());
        ticket.setTransSeq(request.getTransSeq());
        ticket.setTransAmount(request.getTransAmount());
        ticket.setDiscountAmount(request.getDiscountAmount());
        ticket.setPayChannel(request.getPayChannel());
        ticket.setCountingStart(request.getCountingStart());
        ticket.setTicketStatus("ACTIVATED");
        ticket.setAccNoticeStatus("INIT");
        ticket.setActivateTime(now);
        ticket.setCreateTime(now);
        ticket.setUpdateTime(now);
        instanceMapper.upsert(ticket);
        return success(result);
    }

    @Override
    public DailyTicketBaseResult updateAndNotice(DailyTicketUsedNoticeReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (request == null || !StringUtils.hasText(request.getCardNum())) {
            return fail(result, "cardNum不能为空");
        }
        DailyTicketInstance ticket = instanceMapper.selectByCardNum(request.getCardNum());
        if (ticket == null) {
            return fail(result, "车票不存在");
        }
        Date now = new Date();
        ticket.setCountingEnd(request.getCountingEnd());
        ticket.setTicketStatus("USED");
        ticket.setFirstUseTime(now);
        ticket.setAccNoticeStatus("SUCCESS");
        ticket.setAccNoticeTime(now);
        ticket.setUpdateTime(now);
        instanceMapper.markUsed(ticket);
        return success(result);
    }

    @Override
    public DailyTicketBaseResult receivePayResult(DailyTicketPayCallbackReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return fail(result, "orderNo不能为空");
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        if ("success".equalsIgnoreCase(request.getPayResult()) || "SUCCESS".equalsIgnoreCase(request.getPayResult())) {
            markPaySuccess(order, request.getTradeNo(), request.getPayDate() == null ? new Date() : request.getPayDate(), request.getPayAmount());
        } else {
            order.setOrderStatus("PAY_FAILED");
            order.setPayStatus("FAIL");
            order.setUpdateTime(new Date());
            orderMapper.updatePayResult(order);
        }
        insertPayLog(order.getOrderNo(), "CALLBACK", order.getPayChannelCode(), request, result);
        return success(result);
    }

    private void markPaySuccess(DailyTicketOrder order, String tradeNo, Date payDate, Integer payAmount) {
        order.setOrderStatus("PAID");
        order.setPayStatus("PAID");
        order.setTradeNo(tradeNo);
        order.setPayAmount(payAmount == null ? order.getTicketPrice() : payAmount);
        order.setPayDate(payDate);
        order.setUpdateTime(new Date());
        orderMapper.updatePayResult(order);
    }

    private DailyTicketRefund buildRefund(DailyTicketOrder order, String refundType) {
        Date now = new Date();
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setId(nextId());
        refund.setOrderNo(order.getOrderNo());
        refund.setRefundAmount(order.getTicketPrice() == null ? 0 : order.getTicketPrice());
        refund.setRefundStatus("01".equals(refundType) ? "WAIT_VERIFY" : "REFUNDING");
        refund.setRefundType(refundType);
        refund.setVerifyAfterTime("01".equals(refundType) ? plusDays(now, 5) : null);
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        return refund;
    }

    private void insertPayLog(String orderNo, String bizType, String payChannelCode, Object request, Object response) {
        DailyTicketPayLog log = new DailyTicketPayLog();
        log.setId(nextId());
        log.setOrderNo(orderNo);
        log.setBizType(bizType);
        log.setPayChannelCode(payChannelCode);
        log.setRequestBody(JSON.toJSONString(request));
        log.setResponseBody(JSON.toJSONString(response));
        if (response instanceof DailyTicketBaseResult) {
            log.setResultCode(((DailyTicketBaseResult) response).getRetCode());
            log.setResultMsg(((DailyTicketBaseResult) response).getRetMsg());
        } else if (response instanceof DailyTicketPayGatewayResponse) {
            DailyTicketPayGatewayResponse gatewayResponse = (DailyTicketPayGatewayResponse) response;
            log.setResultCode(gatewayResponse.getCode() == null ? null : String.valueOf(gatewayResponse.getCode()));
            log.setResultMsg(gatewayResponse.getMsg());
        }
        log.setCreateTime(new Date());
        payLogMapper.insert(log);
    }

    private String resolveScene(String channelType) {
        if ("1".equals(channelType)) {
            return "app";
        }
        if ("2".equals(channelType)) {
            return "wap";
        }
        return channelType;
    }

    private Map<String, Object> buildDailyTicketPayRequest(DailyTicketOrder order, DailyTicketPayReqDTO request) {
        Map<String, Object> payRequest = new LinkedHashMap<>();
        payRequest.put("orderNo", order.getOrderNo());
        payRequest.put("scene", order.getPayScene());
        payRequest.put("paymentVendor", request.getPayChannelCode());
        payRequest.put("amount", order.getTicketPrice());
        payRequest.put("industryType", payProperties.getIndustryType());
        payRequest.put("subject", payProperties.getSubject());
        payRequest.put("body", payProperties.getBody());
        payRequest.put("thirdUserId", request.getThirdUserId());
        payRequest.put("phone", request.getPhone());
        putIfText(payRequest, "notifyUrl", payProperties.getNotifyUrl());
        putIfText(payRequest, "returnUrl", payProperties.getReturnUrl());
        return payRequest;
    }

    private Map<String, Object> buildDailyTicketRefundRequest(DailyTicketOrder order, DailyTicketRefund refund) {
        Map<String, Object> refundRequest = new LinkedHashMap<>();
        refundRequest.put("orderNo", order.getOrderNo());
        refundRequest.put("refundOrderNo", refund.getId());
        refundRequest.put("refundAmount", refund.getRefundAmount());
        refundRequest.put("refundReason", "日票退款");
        return refundRequest;
    }

    private boolean isGatewaySuccess(DailyTicketPayGatewayResponse response) {
        return response != null && (Boolean.TRUE.equals(response.getSuccess())
                || (response.getCode() != null && response.getCode() == 200));
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }

    private String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String nextOrderNo() {
        return "0E" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%04d", orderSequence.getAndIncrement() % 10000);
    }

    private String nextId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Date plusDays(Date base, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(base);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    private String validateOrderRequest(DailyTicketOrderReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        if (!StringUtils.hasText(request.getUserId())) {
            return "userId不能为空";
        }
        return null;
    }

    private String validateOrderNo(String orderNo, String orderType) {
        if (!ORDER_TYPE_DAILY_TICKET.equals(orderType)) {
            return "orderType必须为1";
        }
        if (!StringUtils.hasText(orderNo)) {
            return "orderNo不能为空";
        }
        return null;
    }

    private <T extends DailyTicketBaseResult> T success(T result) {
        result.setRetCode(RET_SUCCESS);
        result.setRetMsg("成功");
        return result;
    }

    private <T extends DailyTicketBaseResult> T fail(T result, String message) {
        result.setRetCode(RET_FAIL);
        result.setRetMsg(message);
        return result;
    }
}
