package com.chinasofti.huateng.dailyticket.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayClient;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.config.DailyTicketPayProperties;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketPayLogMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketRefundDetailMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketUsageLogMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.DailyTicketPayLog;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefund;
import com.chinasofti.huateng.dailyticket.model.DailyTicketRefundDetail;
import com.chinasofti.huateng.dailyticket.model.DailyTicketUsageLog;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderView;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundView;
import com.chinasofti.huateng.dailyticket.page.TravelTicketSubRefundRequest;
import com.chinasofti.huateng.dailyticket.service.DailyTicketService;
import com.chinasofti.huateng.dailyticket.service.DailyTicketRefundNotifyService;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketActivateReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoResult;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderResult;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** 日票业务服务默认实现。 */
@Service
public class DailyTicketServiceImpl implements DailyTicketService {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketServiceImpl.class);

    private static final String RET_SUCCESS = "0000";
    private static final String RET_FAIL = "9999";
    private static final String ORDER_TYPE_DAILY_TICKET = "1";
    private static final String ORDER_TYPE_TRAVEL_TICKET = "2";

    /**
     * 旅游票单次购买张数上限。旅游票下单按张数循环 INSERT，不设上限等于把 for 循环次数交给外部输入。
     * 上限值待业务确认，暂按 20 张。
     */
    private static final int MAX_TRAVEL_TICKET_COUNT = 20;

    /**
     * 日票实例状态机（{@code DAILY_TICKET_INSTANCE.TICKET_STATUS}）：
     * {@code ACTIVATED -> USED / EXPIRED}（出站扣次）、{@code ACTIVATED -> REFUND_LOCKED}（发起核验退款）、
     * {@code REFUND_LOCKED -> REFUNDED}（放款成功）、{@code REFUND_LOCKED -> ACTIVATED}（退款失败回退）。
     * {@code REFUND_LOCKED} 与 {@code REFUNDED} 都不在进站白名单（{@code selectForEntryCheck}）里，
     * 这是「已申请退款的票不能再乘坐」的唯一落点，NEVER 把它们加进那个白名单。
     */
    private static final String TICKET_STATUS_ACTIVATED = "ACTIVATED";
    private static final String TICKET_STATUS_USED = "USED";
    private static final String TICKET_STATUS_EXPIRED = "EXPIRED";
    private static final String TICKET_STATUS_REFUND_LOCKED = "REFUND_LOCKED";
    private static final String TICKET_STATUS_REFUNDED = "REFUNDED";

    private final AtomicInteger orderSequence = new AtomicInteger(1);
    private final DailyTicketPayGatewayClient payGatewayClient;
    private final DailyTicketPayProperties payProperties;
    private final DailyTicketOrderMapper orderMapper;
    private final TravelTicketOrderMapper travelOrderMapper;
    private final DailyTicketInstanceMapper instanceMapper;
    private final DailyTicketPayLogMapper payLogMapper;
    private final DailyTicketRefundMapper refundMapper;
    private final DailyTicketRefundDetailMapper refundDetailMapper;
    private final DailyTicketUsageLogMapper usageLogMapper;
    private final DailyTicketRefundNotifyService refundNotifyService;

    public DailyTicketServiceImpl(DailyTicketPayGatewayClient payGatewayClient,
                                  DailyTicketPayProperties payProperties,
                                  DailyTicketOrderMapper orderMapper,
                                  TravelTicketOrderMapper travelOrderMapper,
                                  DailyTicketInstanceMapper instanceMapper,
                                  DailyTicketPayLogMapper payLogMapper,
                                  DailyTicketRefundMapper refundMapper,
                                  DailyTicketRefundDetailMapper refundDetailMapper,
                                  DailyTicketUsageLogMapper usageLogMapper,
                                  DailyTicketRefundNotifyService refundNotifyService) {
        this.payGatewayClient = payGatewayClient;
        this.payProperties = payProperties;
        this.orderMapper = orderMapper;
        this.travelOrderMapper = travelOrderMapper;
        this.instanceMapper = instanceMapper;
        this.payLogMapper = payLogMapper;
        this.refundMapper = refundMapper;
        this.refundDetailMapper = refundDetailMapper;
        this.usageLogMapper = usageLogMapper;
        this.refundNotifyService = refundNotifyService;
    }

    /** IF8A-70 旅游票下单。 */
    @Override
    public TravelTicketOrderResult requestTravelOrder(TravelTicketOrderReqDTO request) {
        TravelTicketOrderResult result = new TravelTicketOrderResult();
        String validMsg = validateTravelOrderRequest(request);
        if (validMsg != null) {
            log.warn("IF8A-70 旅游票下单参数校验失败, msg={}, request={}", validMsg, request);
            return fail(result, validMsg);
        }

        Date now = new Date();
        int ticketPrice = request.getTotalAmount() / request.getTicketCount();
        int ticketCount = request.getTicketCount();
        String travelOrderNo = nextTravelOrderNo();

        List<String> subOrderNos = new ArrayList<>(ticketCount);
        for (int i = 0; i < ticketCount; i++) {
            DailyTicketOrder sub = buildTravelSubOrder(request, travelOrderNo, ticketPrice, now);
            orderMapper.insert(sub);
            subOrderNos.add(sub.getOrderNo());
        }

        travelOrderMapper.insert(buildTravelMainOrder(request, travelOrderNo, ticketPrice, ticketCount, now));

        log.info("IF8A-70 旅游票下单完成, orderNo={}, ticketCount={}, totalAmount={}",
                travelOrderNo, ticketCount, request.getTotalAmount());
        success(result);
        result.setOrderNo(travelOrderNo);
        result.setSubOrders(String.join(",", subOrderNos));
        return result;
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
        if (ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return requestTravelPay(request);
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        if (!"CREATED".equals(order.getOrderStatus()) && !"PAYING".equals(order.getOrderStatus())) {
            return fail(result, "订单状态不允许支付");
        }
        if ("PAYING".equals(order.getOrderStatus()) || "PAYING".equals(order.getPayStatus())) {
            queryAndRefreshPayResult(order);
            DailyTicketOrder latest = orderMapper.selectByOrderNo(order.getOrderNo());
            if (latest != null && "PAID".equals(latest.getOrderStatus()) && "PAID".equals(latest.getPayStatus())) {
                return success(result);
            }
            if (latest != null && "PAY_FAILED".equals(latest.getOrderStatus()) && "FAIL".equals(latest.getPayStatus())) {
                return fail(result, "支付已失败，请重新下单");
            }
            return fail(result, "支付处理中，请查询支付结果");
        }

        Date now = new Date();
        order.setThirdUserId(request.getThirdUserId());
        order.setPayChannelCode(request.getPayChannelCode());
        order.setChannelType(request.getChannelType());
        order.setPayScene(resolveScene(request.getChannelType()));
        order.setOrderStatus("PAYING");
        order.setPayStatus("PAYING");
        order.setUpdateTime(now);
        if (orderMapper.updatePayRequest(order) == 0) {
            return fail(result, "订单状态已变更，请查询支付结果");
        }

        Map<String, Object> payRequest = buildDailyTicketPayRequest(order, request);
        log.info("日票服务准备调用支付网关支付接口 orderNo={}, request={}", order.getOrderNo(), JSON.toJSONString(payRequest));
        DailyTicketPayGatewayResponse payResponse = payGatewayClient.requestPay(payRequest);
        log.info("日票服务调用支付网关支付接口完成 orderNo={}, response={}", order.getOrderNo(), JSON.toJSONString(payResponse));
        insertPayLog(order.getOrderNo(), "PAY", request.getPayChannelCode(), payRequest, payResponse);
        if (!isGatewaySuccess(payResponse)) {
            if (isGatewayExplicitFailure(payResponse)) {
                markPayFailed(order);
                return fail(result, payResponse.getMsg());
            }
            return fail(result, "日票支付网关结果未知，请使用原订单重试支付");
        }
        updatePaymentOrderNo(order, stringValue(payResponse.getData() == null ? null : payResponse.getData().get("orderNo"), null));

        success(result);
        result.setSignType("01");
        result.setSign("");
        result.setPayChannelCode(request.getPayChannelCode());
        result.setPaymentInfo(stringValue(payResponse.getData() == null ? null : payResponse.getData().get("data"), null));
        result.setDiscountInfo(null);
        return result;
    }

    /** 旅游票支付复用 IF8A-61 网关协议，但商户订单号和金额均使用旅游票主单。 */
    private DailyTicketPayResult requestTravelPay(DailyTicketPayReqDTO request) {
        DailyTicketPayResult result = new DailyTicketPayResult();
        TravelTicketOrder order = travelOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "旅游票主订单不存在");
        }
        if (!"CREATED".equals(order.getOrderStatus()) && !"PAYING".equals(order.getOrderStatus())) {
            return fail(result, "旅游票主订单状态不允许支付");
        }
        if ("PAYING".equals(order.getOrderStatus()) || "PAYING".equals(order.getPayStatus())) {
            queryAndRefreshTravelPayResult(order);
            TravelTicketOrder latest = travelOrderMapper.selectByOrderNo(order.getOrderNo());
            if (latest != null && "PAID".equals(latest.getOrderStatus())
                    && "PAID".equals(latest.getPayStatus())) {
                return success(result);
            }
            if (latest != null && "PAY_FAILED".equals(latest.getOrderStatus())
                    && "FAIL".equals(latest.getPayStatus())) {
                return fail(result, "支付已失败，请重新下单");
            }
            return fail(result, "支付处理中，请查询支付结果");
        }

        Date now = new Date();
        order.setThirdUserId(request.getThirdUserId());
        order.setPayChannelCode(request.getPayChannelCode());
        order.setChannelType(request.getChannelType());
        order.setPayScene(resolveScene(request.getChannelType()));
        order.setOrderStatus("PAYING");
        order.setPayStatus("PAYING");
        order.setUpdateTime(now);
        if (travelOrderMapper.updatePayRequest(order) == 0) {
            return fail(result, "旅游票主订单状态已变更，请查询支付结果");
        }

        Map<String, Object> payRequest = buildTravelTicketPayRequest(order, request);
        DailyTicketPayGatewayResponse payResponse = payGatewayClient.requestPay(payRequest);
        insertPayLog(order.getOrderNo(), "PAY", request.getPayChannelCode(), payRequest, payResponse);
        if (!isGatewaySuccess(payResponse)) {
            if (isGatewayExplicitFailure(payResponse)) {
                markTravelPayFailed(order);
                return fail(result, payResponse == null ? "旅游票支付失败" : payResponse.getMsg());
            }
            return fail(result, "旅游票支付网关结果未知，请查询支付结果");
        }
        updateTravelPaymentOrderNo(order,
                stringValue(payResponse.getData() == null ? null : payResponse.getData().get("orderNo"), null));
        success(result);
        result.setSignType("01");
        result.setSign("");
        result.setPayChannelCode(request.getPayChannelCode());
        result.setPaymentInfo(stringValue(payResponse.getData() == null ? null : payResponse.getData().get("data"), null));
        result.setDiscountInfo(null);
        return result;
    }

    private DailyTicketPayQueryResult queryTravelPayResult(DailyTicketOrderNoReqDTO request, boolean operationQuery) {
        DailyTicketPayQueryResult result = new DailyTicketPayQueryResult();
        TravelTicketOrder order = travelOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "旅游票主订单不存在");
        }
        if ("PAYING".equals(order.getOrderStatus()) || "PAYING".equals(order.getPayStatus())) {
            queryAndRefreshTravelPayResult(order);
            order = travelOrderMapper.selectByOrderNo(request.getOrderNo());
        }
        success(result);
        result.setTradeNo(order.getTradeNo());
        result.setPayResult("PAID".equals(order.getPayStatus()) ? "success"
                : "FAIL".equals(order.getPayStatus()) ? "failed" : "processing");
        result.setPayAmount(order.getPayAmount() == null ? order.getTotalAmount() : order.getPayAmount());
        result.setPayDate(order.getPayDate());
        result.setDiscountInfo(null);
        result.setPayChannel(order.getPayChannelCode());
        result.setChannelDiscount(0);
        result.setCouponDiscount(0);
        return result;
    }

    @Override
    public DailyTicketPayQueryResult requestPayResult(DailyTicketOrderNoReqDTO request) {
        DailyTicketPayQueryResult result = new DailyTicketPayQueryResult();
        String validMsg = validateOrderNo(request == null ? null : request.getOrderNo(), request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return fail(result, validMsg);
        }
        if (ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return queryTravelPayResult(request, false);
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        if ("PAYING".equals(order.getOrderStatus()) || "PAYING".equals(order.getPayStatus())) {
            queryAndRefreshPayResult(order);
            order = orderMapper.selectByOrderNo(request.getOrderNo());
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
    public DailyTicketPayQueryResult queryPayTicket(DailyTicketOrderNoReqDTO request) {
        DailyTicketPayQueryResult result = new DailyTicketPayQueryResult();
        String validMsg = validateOrderNo(request == null ? null : request.getOrderNo(), request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return fail(result, validMsg);
        }
        if (ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return queryTravelPayResult(request, true);
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        if ("PAYING".equals(order.getOrderStatus()) || "PAYING".equals(order.getPayStatus())) {
            queryAndRefreshPayResult(order);
            order = orderMapper.selectByOrderNo(request.getOrderNo());
        }
        if (order == null) {
            return fail(result, "订单状态查询失败");
        }
        success(result);
        result.setTradeNo(order.getTradeNo());
        result.setPayResult("PAID".equals(order.getPayStatus()) ? "success"
                : "FAIL".equals(order.getPayStatus()) ? "failed" : "processing");
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
        if (ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return requestTravelRefund(request.getOrderNo());
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        DailyTicketRefund existingRefund = refundMapper.selectByOrderNo(order.getOrderNo());
        if (existingRefund != null) {
            return buildExistingRefundResult(result, existingRefund);
        }
        if (!"PAID".equals(order.getOrderStatus()) || !"PAID".equals(order.getPayStatus())) {
            return fail(result, "订单未支付成功，不允许退款");
        }
        if (!StringUtils.hasText(order.getPaymentOrderNo())) {
            queryAndRefreshPayResult(order);
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

        if ("01".equals(refundType)) {
            if (!lockTicketForRefund(ticket)) {
                log.warn("日票核验退款：锁票失败，车票状态已变更 orderNo={}, instanceId={}",
                        order.getOrderNo(), ticket.getId());
                return fail(result, "车票状态已变更，不允许退款");
            }
            refundMapper.insert(refund);
            orderMapper.updateOrderStatus(order.getOrderNo(), "REFUNDING");
            result.setRefundType(refundType);
            result.setOrderNo(order.getOrderNo());
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc("已激活车票进入5天核验退款流程");
            result.setRefundAmount(String.valueOf(refund.getRefundAmount()));
            return success(result);
        }
        refundMapper.insert(refund);

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

    /** 旅游票 APP 退款只允许主单整单退款；子单退款由运营接口单独实现。 */
    private DailyTicketRefundResult requestTravelRefund(String parentOrderNo) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(parentOrderNo);
        if (parent == null) {
            return fail(result, "旅游票主订单不存在");
        }
        DailyTicketRefund existing = refundMapper.selectByOrderNo(parentOrderNo);
        if (existing != null) {
            return buildExistingRefundResult(result, existing);
        }
        if (!"PAID".equals(parent.getOrderStatus()) || !"PAID".equals(parent.getPayStatus())) {
            return fail(result, "旅游票主订单未支付成功，不允许退款");
        }
        if (!StringUtils.hasText(parent.getPaymentOrderNo())) {
            queryAndRefreshTravelPayResult(parent);
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
        refundMapper.insert(refund);
        insertTravelRefundDetails(refund, parentOrderNo, children);
        travelOrderMapper.updateOrderStatus(parentOrderNo, "REFUNDING");

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

    @Override
    public DailyTicketRefundResult queryRefundTicket(DailyTicketOrderNoReqDTO request) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        String validMsg = validateOrderNo(request == null ? null : request.getOrderNo(), request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return fail(result, validMsg);
        }
        if (ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return queryTravelRefundTicket(request.getOrderNo());
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        DailyTicketRefund refund = refundMapper.selectByOrderNo(request.getOrderNo());
        if (order == null || refund == null) {
            return fail(result, "退款记录不存在");
        }
        if (!"REFUNDING".equals(refund.getRefundStatus()) && !"FAILED".equals(refund.getRefundStatus())) {
            return buildExistingRefundResult(result, refund);
        }

        restorePlatformRefundNoFromPayLog(order.getOrderNo(), refund);
        if (!StringUtils.hasText(refund.getPlatformRefundNo())) {
            return fail(result, "支付平台退款单号缺失，无法执行双字段退款查询");
        }
        Map<String, Object> queryRequest = new LinkedHashMap<>();
        queryRequest.put("refundOrderNo", refund.getPlatformRefundNo());
        queryRequest.put("merchantRefundNo", refund.getRefundOrderNo());
        log.info("日票服务准备调用支付网关退款查询接口 orderNo={}, request={}",
                order.getOrderNo(), JSON.toJSONString(queryRequest));
        DailyTicketPayGatewayResponse queryResponse = payGatewayClient.requestRefundQuery(queryRequest);
        log.info("日票服务调用支付网关退款查询接口完成 orderNo={}, response={}",
                order.getOrderNo(), JSON.toJSONString(queryResponse));
        insertPayLog(order.getOrderNo(), "REFUND_QUERY", order.getPayChannelCode(), queryRequest, queryResponse);
        if (!isGatewaySuccess(queryResponse) || queryResponse.getData() == null) {
            return fail(result, queryResponse == null ? "退款结果查询失败" : queryResponse.getMsg());
        }

        persistPlatformRefundNoIfChanged(refund, queryResponse.getData());
        String status = stringValue(queryResponse.getData().get("status"), null);
        if (isRefundSuccessStatus(status)) {
            markRefunded(order, refund, queryResponse.getData());
            return buildExistingRefundResult(result, refund);
        }
        if (isRefundFailedStatus(status)) {
            markRefundFailed(order, refund, queryResponse.getData());
            result.setRefundType(refund.getRefundType());
            result.setOrderNo(refund.getRefundOrderNo());
            result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
            result.setRefundResult("FAILED");
            result.setRefundResultDesc("支付平台退款失败，可发起退款重试");
            return success(result);
        }

        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundResult("PROCESSING");
        result.setRefundResultDesc("支付平台退款处理中");
        return success(result);
    }

    private DailyTicketRefundResult queryTravelRefundTicket(String orderNo) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        DailyTicketRefund refund = refundMapper.selectByOrderNo(orderNo);
        if (refund == null) {
            return fail(result, "退款记录不存在");
        }
        String parentOrderNo = StringUtils.hasText(refund.getParentOrderNo()) ? refund.getParentOrderNo() : refund.getOrderNo();
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(parentOrderNo);
        if (parent == null) {
            return fail(result, "旅游票主订单不存在");
        }
        if (!"REFUNDING".equals(refund.getRefundStatus()) && !"FAILED".equals(refund.getRefundStatus())) {
            return buildExistingRefundResult(result, refund);
        }
        restorePlatformRefundNoFromPayLog(refund.getOrderNo(), refund);
        if (!StringUtils.hasText(refund.getPlatformRefundNo())) {
            return fail(result, "支付平台退款单号缺失，无法执行退款查询");
        }
        Map<String, Object> queryRequest = new LinkedHashMap<>();
        queryRequest.put("refundOrderNo", refund.getPlatformRefundNo());
        queryRequest.put("merchantRefundNo", refund.getRefundOrderNo());
        DailyTicketPayGatewayResponse response = payGatewayClient.requestRefundQuery(queryRequest);
        insertPayLog(refund.getOrderNo(), "REFUND_QUERY", parent.getPayChannelCode(), queryRequest, response);
        if (!isGatewaySuccess(response) || response.getData() == null) {
            return fail(result, response == null ? "退款结果查询失败" : response.getMsg());
        }
        persistPlatformRefundNoIfChanged(refund, response.getData());
        String status = stringValue(response.getData().get("status"), null);
        if (isRefundSuccessStatus(status)) {
            markTravelRefunded(parent, refund, response.getData());
            return buildExistingRefundResult(result, refund);
        }
        if (isRefundFailedStatus(status)) {
            markTravelRefundFailed(parent, refund, response.getData());
            return buildExistingRefundResult(result, refund);
        }
        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundResult("PROCESSING");
        result.setRefundResultDesc("支付平台退款处理中");
        return success(result);
    }

    @Override
    public DailyTicketRefundResult retryRefundTicket(DailyTicketOrderNoReqDTO request) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        String validMsg = validateOrderNo(request == null ? null : request.getOrderNo(), request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return fail(result, validMsg);
        }
        if (ORDER_TYPE_TRAVEL_TICKET.equals(request.getOrderType())) {
            return retryTravelRefundTicket(request.getOrderNo());
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        DailyTicketRefund refund = refundMapper.selectByOrderNo(request.getOrderNo());
        if (order == null || refund == null) {
            return fail(result, "退款记录不存在");
        }
        if (!"00".equals(refund.getRefundType())) {
            return fail(result, "核验退款不支持支付平台重试");
        }
        if (!"REFUNDING".equals(refund.getRefundStatus()) && !"FAILED".equals(refund.getRefundStatus())) {
            return buildExistingRefundResult(result, refund);
        }

        DailyTicketRefundResult queryResult = queryRefundTicket(request);
        if (!RET_SUCCESS.equals(queryResult.getRetCode())
                || (!"PROCESSING".equals(queryResult.getRefundResult()) && !"FAILED".equals(queryResult.getRefundResult()))) {
            return queryResult;
        }

        Map<String, Object> refundRequest = buildDailyTicketRefundRequest(order, refund);
        log.info("日票服务准备重试支付网关退款接口 orderNo={}, request={}",
                order.getOrderNo(), JSON.toJSONString(refundRequest));
        DailyTicketPayGatewayResponse retryResponse = payGatewayClient.requestRefund(refundRequest);
        log.info("日票服务重试支付网关退款接口完成 orderNo={}, response={}",
                order.getOrderNo(), JSON.toJSONString(retryResponse));
        insertPayLog(order.getOrderNo(), "REFUND_RETRY", order.getPayChannelCode(), refundRequest, retryResponse);
        if (!isGatewaySuccess(retryResponse)) {
            return fail(result, retryResponse == null ? "退款重试调用失败" : retryResponse.getMsg());
        }

        String refundTime = retryResponse.getData() == null ? null
                : stringValue(retryResponse.getData().get("refundTime"), null);
        if (StringUtils.hasText(refundTime)) {
            markRefunded(order, refund, retryResponse.getData());
            return buildExistingRefundResult(result, refund);
        }
        updatePlatformRefundNo(refund, retryResponse.getData());
        markRefunding(order, refund);
        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundResult("PROCESSING");
        result.setRefundResultDesc("退款重试已提交，请查询退款结果");
        return success(result);
    }

    private DailyTicketRefundResult retryTravelRefundTicket(String orderNo) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        DailyTicketRefund refund = refundMapper.selectByOrderNo(orderNo);
        if (refund == null) {
            return fail(result, "退款记录不存在");
        }
        String parentOrderNo = StringUtils.hasText(refund.getParentOrderNo()) ? refund.getParentOrderNo() : refund.getOrderNo();
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(parentOrderNo);
        if (parent == null || !StringUtils.hasText(parent.getPaymentOrderNo())) {
            return fail(result, "旅游票原支付订单号缺失，不允许退款重试");
        }
        if (!"REFUNDING".equals(refund.getRefundStatus()) && !"FAILED".equals(refund.getRefundStatus())) {
            return buildExistingRefundResult(result, refund);
        }
        DailyTicketRefundResult queryResult = queryTravelRefundTicket(orderNo);
        if (!RET_SUCCESS.equals(queryResult.getRetCode())
                || (!"PROCESSING".equals(queryResult.getRefundResult()) && !"FAILED".equals(queryResult.getRefundResult()))) {
            return queryResult;
        }
        Map<String, Object> retryRequest = "TRAVEL_SUB".equals(refund.getRefundScope())
                ? buildTravelSubRefundRequest(parent, refund)
                : buildTravelRefundRequest(parent, refund);
        DailyTicketPayGatewayResponse response = payGatewayClient.requestRefund(retryRequest);
        insertPayLog(refund.getOrderNo(), "REFUND_RETRY", parent.getPayChannelCode(), retryRequest, response);
        if (!isGatewaySuccess(response)) {
            return fail(result, response == null ? "退款重试调用失败" : response.getMsg());
        }
        String refundTime = response.getData() == null ? null
                : stringValue(response.getData().get("refundTime"), null);
        if (StringUtils.hasText(refundTime)) {
            markTravelRefunded(parent, refund, response.getData());
            return buildExistingRefundResult(result, refund);
        }
        updatePlatformRefundNo(refund, response.getData());
        refund.setRefundStatus("REFUNDING");
        refund.setRefundDate(null);
        refund.setUpdateTime(new Date());
        refundMapper.updateResult(refund);
        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundResult("PROCESSING");
        result.setRefundResultDesc("退款重试已提交，请查询退款结果");
        return success(result);
    }

    @Override
    public DailyTicketRefundResult resubmitRefundTicket(DailyTicketOrderNoReqDTO request) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        String validMsg = validateOrderNo(request == null ? null : request.getOrderNo(), request == null ? null : request.getOrderType());
        if (validMsg != null) {
            return fail(result, validMsg);
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        DailyTicketRefund refund = refundMapper.selectByOrderNo(request.getOrderNo());
        if (order == null || refund == null) {
            return fail(result, "退款记录不存在");
        }
        String refundStatus = refund.getRefundStatus();
        if (!"REFUNDING".equals(refundStatus) && !"FAILED".equals(refundStatus) && !"WAIT_VERIFY".equals(refundStatus)) {
            return buildExistingRefundResult(result, refund);
        }
        if ("WAIT_VERIFY".equals(refundStatus)
                && refund.getVerifyAfterTime() != null
                && refund.getVerifyAfterTime().after(new Date())) {
            return fail(result, "核验退款观察期未满，不允许重提交");
        }
        if ("01".equals(refund.getRefundType())) {
            DailyTicketInstance ticket = instanceMapper.selectByOrderNo(order.getOrderNo());
            if (ticket == null || !TICKET_STATUS_REFUND_LOCKED.equals(ticket.getTicketStatus())) {
                log.warn("日票核验退款重提交：票不在 REFUND_LOCKED，拒绝放款 orderNo={}, ticketStatus={}",
                        order.getOrderNo(), ticket == null ? null : ticket.getTicketStatus());
                return fail(result, "车票状态已变更，不允许放款，请人工核验");
            }
        }
        restorePlatformRefundNoFromPayLog(order.getOrderNo(), refund);
        if (StringUtils.hasText(refund.getPlatformRefundNo())) {
            return fail(result, "支付平台已受理该退款单，请走退款结果查询或退款重试");
        }
        if (!StringUtils.hasText(order.getPaymentOrderNo())) {
            return fail(result, "原支付订单号缺失，不允许退款");
        }

        Map<String, Object> resubmitRequest = buildDailyTicketRefundRequest(order, refund);
        log.info("日票服务准备重提交支付网关退款接口 orderNo={}, refundOrderNo={}, refundStatus={}, request={}",
                order.getOrderNo(), refund.getRefundOrderNo(), refundStatus, JSON.toJSONString(resubmitRequest));
        DailyTicketPayGatewayResponse resubmitResponse = payGatewayClient.requestRefund(resubmitRequest);
        log.info("日票服务重提交支付网关退款接口完成 orderNo={}, response={}",
                order.getOrderNo(), JSON.toJSONString(resubmitResponse));
        insertPayLog(order.getOrderNo(), "REFUND_RESUBMIT", order.getPayChannelCode(), resubmitRequest, resubmitResponse);
        if (!isGatewaySuccess(resubmitResponse)) {
            return fail(result, resubmitResponse == null ? "退款重提交调用失败" : resubmitResponse.getMsg());
        }

        String refundTime = resubmitResponse.getData() == null ? null
                : stringValue(resubmitResponse.getData().get("refundTime"), null);
        if (StringUtils.hasText(refundTime)) {
            markRefunded(order, refund, resubmitResponse.getData());
            return buildExistingRefundResult(result, refund);
        }
        updatePlatformRefundNo(refund, resubmitResponse.getData());
        markRefunding(order, refund);
        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundResult("PROCESSING");
        result.setRefundResultDesc("退款重提交已提交，请查询退款结果");
        return success(result);
    }

    @Override
    public ResultVO<PageInfo<DailyTicketRefundOrderView>> pageRefundOrders(DailyTicketRefundOrderQuery query) {
        DailyTicketRefundOrderQuery safeQuery = query == null ? new DailyTicketRefundOrderQuery() : query;
        PageInfo<DailyTicketRefundOrderView> pageInfo = PageHelper
                .startPage(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()))
                .doSelectPageInfo(() -> orderMapper.selectRefundOrders(safeQuery));
        return ResultMapper.ok(pageInfo);
    }

    @Override
    public ResultVO<List<DailyTicketRefundOrderView>> listTravelSubRefundOrders(String parentOrderNo) {
        if (!StringUtils.hasText(parentOrderNo)) {
            return ResultMapper.illegalParams("旅游票主单号不能为空");
        }
        return ResultMapper.ok(orderMapper.selectTravelSubRefundOrders(parentOrderNo));
    }

    @Override
    public DailyTicketRefundResult requestTravelSubRefund(TravelTicketSubRefundRequest request) {
        DailyTicketRefundResult result = new DailyTicketRefundResult();
        if (request == null || !StringUtils.hasText(request.getParentOrderNo())) {
            return fail(result, "旅游票主单号不能为空");
        }
        if (!StringUtils.hasText(request.getSubOrderNo())) {
            return fail(result, "旅游票子单号不能为空");
        }
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(request.getParentOrderNo());
        DailyTicketOrder child = orderMapper.selectByOrderNo(request.getSubOrderNo());
        if (parent == null || child == null || !request.getParentOrderNo().equals(child.getParentOrderNo())) {
            return fail(result, "旅游票主子单关系不存在");
        }
        DailyTicketRefund existing = refundMapper.selectByOrderNo(child.getOrderNo());
        if (existing != null) {
            return buildExistingRefundResult(result, existing);
        }
        DailyTicketRefund parentRefund = refundMapper.selectByOrderNo(parent.getOrderNo());
        if (parentRefund != null && !"FAILED".equals(parentRefund.getRefundStatus())) {
            return fail(result, "旅游票主单已存在整单退款，不允许子单退款");
        }
        if (!"PAID".equals(parent.getPayStatus())
                || (!"PAID".equals(parent.getOrderStatus()) && !"PARTIAL_USED".equals(parent.getOrderStatus())
                && !"PARTIAL_REFUNDED".equals(parent.getOrderStatus()))) {
            return fail(result, "旅游票主单未支付成功，不允许子单退款");
        }
        if (!StringUtils.hasText(parent.getPaymentOrderNo())) {
            return fail(result, "原支付订单号缺失，不允许退款");
        }
        DailyTicketInstance ticket = instanceMapper.selectByOrderNo(child.getOrderNo());
        if (ticket != null && !TICKET_STATUS_ACTIVATED.equals(ticket.getTicketStatus())) {
            return fail(result, "旅游票子单已使用或已退款，不允许退款");
        }
        if (ticket != null && !lockTicketForRefund(ticket)) {
            return fail(result, "旅游票子单状态已变更，不允许退款");
        }

        DailyTicketRefund refund = buildTravelSubRefund(parent, child, request);
        refundMapper.insert(refund);
        List<DailyTicketOrder> details = new ArrayList<>();
        details.add(child);
        insertTravelRefundDetails(refund, parent.getOrderNo(), details);

        Map<String, Object> refundRequest = buildTravelSubRefundRequest(parent, refund);
        DailyTicketPayGatewayResponse response;
        try {
            response = payGatewayClient.requestRefund(refundRequest);
        } catch (RuntimeException e) {
            insertPayLog(child.getOrderNo(), "REFUND", parent.getPayChannelCode(), refundRequest,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
            result.setRefundType("00");
            result.setOrderNo(refund.getRefundOrderNo());
            result.setRefundAmount(String.valueOf(refund.getRefundAmount()));
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc("退款已提交，结果待确认");
            return success(result);
        }
        insertPayLog(child.getOrderNo(), "REFUND", parent.getPayChannelCode(), refundRequest, response);
        if (!isGatewaySuccess(response)) {
            refund.setRefundStatus("FAILED");
            refund.setUpdateTime(new Date());
            refundMapper.updateResult(refund);
            refundDetailMapper.updateStatusByRefundOrderNo(refund.getRefundOrderNo(), "FAILED");
            releaseTicketLock(child.getOrderNo());
            return fail(result, response == null ? "旅游票子单退款网关调用失败" : response.getMsg());
        }
        String refundTime = response.getData() == null ? null
                : stringValue(response.getData().get("refundTime"), null);
        if (StringUtils.hasText(refundTime)) {
            markTravelRefunded(parent, refund, response.getData());
        } else {
            updatePlatformRefundNo(refund, response.getData());
            refund.setUpdateTime(new Date());
            refundMapper.updateResult(refund);
        }
        result.setRefundType("00");
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount()));
        result.setRefundResult(StringUtils.hasText(refundTime) ? "SUCCESS" : "PROCESSING");
        result.setRefundResultDesc(StringUtils.hasText(refundTime) ? "退款完成" : "退款申请已提交，请查询退款结果");
        return success(result);
    }

    @Override
    public ResultVO<PageInfo<DailyTicketRefundView>> pageRefundRecords(DailyTicketRefundQuery query) {
        DailyTicketRefundQuery safeQuery = query == null ? new DailyTicketRefundQuery() : query;
        PageInfo<DailyTicketRefundView> pageInfo = PageHelper
                .startPage(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()))
                .doSelectPageInfo(() -> refundMapper.selectRefunds(safeQuery));
        return ResultMapper.ok(pageInfo);
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

    /** 激活日票（IF8A-32）。 */
    @Override
    public DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return fail(result, "orderNo不能为空");
        }
        if (!StringUtils.hasText(request.getCardNum())) {
            return fail(result, "cardNum不能为空");
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        if (!canActivate(order)) {
            return fail(result, "订单未支付，不能激活");
        }

        DailyTicketInstance exists = instanceMapper.selectByOrderNo(request.getOrderNo());
        if (exists != null && !TICKET_STATUS_ACTIVATED.equals(exists.getTicketStatus())) {
            log.info("日票已开始使用或已终态，激活请求短路返回 orderNo={} ticketStatus={} cardNum={}",
                    request.getOrderNo(), exists.getTicketStatus(), exists.getCardNum());
            return success(result);
        }
        if (exists != null && !request.getCardNum().equals(exists.getCardNum())) {
            log.warn("日票重复激活但卡号与首次不一致 orderNo={} 原cardNum={} 本次cardNum={}",
                    request.getOrderNo(), exists.getCardNum(), request.getCardNum());
            return fail(result, "卡号与已激活车票不一致");
        }

        Date now = new Date();
        DailyTicketInstance ticket = new DailyTicketInstance();
        ticket.setId(exists != null ? exists.getId() : nextId());
        ticket.setOrderNo(request.getOrderNo());
        ticket.setThirdUserId(request.getThirdUserId());
        ticket.setCardNum(request.getCardNum());
        ticket.setCardIssue(request.getCardIssue());
        ticket.setAppCardType(order.getCardType());
        ticket.setCodeTicketType(CardTypeCodeEnum.QR_POSTPAID.getCode());
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
        ticket.setTicketStatus(TICKET_STATUS_ACTIVATED);
        ticket.setAccNoticeStatus("INIT");
        ticket.setActivateTime(now);
        ticket.setCreateTime(now);
        ticket.setUpdateTime(now);
        instanceMapper.upsert(ticket);
        return success(result);
    }

    /**
     * 激活支付口径：
     * <ul>
     *     <li>独立日票沿用原规则：子单自身 ORDER_STATUS=PAID 且 PAY_STATUS=PAID；</li>
     *     <li>旅游票子单不写独立支付流水，子单保持 CREATED/INIT，回看 PARENT_ORDER_NO
     *         对应主单，主单两个支付字段都为 PAID 才允许激活。</li>
     * </ul>
     */
    private boolean canActivate(DailyTicketOrder order) {
        if (order == null) {
            return false;
        }
        if (StringUtils.hasText(order.getParentOrderNo())) {
            TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(order.getParentOrderNo());
            return parent != null
                    && "PAID".equals(parent.getOrderStatus())
                    && "PAID".equals(parent.getPayStatus());
        }
        return "PAID".equals(order.getOrderStatus())
                && "PAID".equals(order.getPayStatus());
    }

    /** APP 首次使用通知（IF8A-33）：写入有效期截止时间并置「已开始使用」。 */
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
        if (isTicketLockedForRefund(ticket.getTicketStatus())) {
            log.warn("日票首次使用通知：车票处于退款占用态，拒绝置已使用, cardNum={}, ticketStatus={}",
                    request.getCardNum(), ticket.getTicketStatus());
            return fail(result, "车票已申请退款，不允许使用");
        }
        Date now = new Date();
        ticket.setCountingEnd(request.getCountingEnd());
        ticket.setTicketStatus(TICKET_STATUS_USED);
        ticket.setFirstUseTime(ticket.getFirstUseTime() == null ? now : ticket.getFirstUseTime());
        ticket.setAccNoticeStatus("SUCCESS");
        ticket.setAccNoticeTime(now);
        ticket.setUpdateTime(now);
        int marked = instanceMapper.markUsed(ticket);
        if (marked == 0) {
            log.error("日票首次使用通知：实例状态回写影响 0 行, cardNum={}, instanceId={}",
                    request.getCardNum(), ticket.getId());
        }
        refreshTravelParentSummaryBySubOrder(ticket.getOrderNo());
        log.info("日票首次使用通知完成, cardNum={}, countingEnd={}, ticketStatus={}",
                request.getCardNum(), request.getCountingEnd(), TICKET_STATUS_USED);
        return success(result);
    }

    @Override
    public DailyTicketBaseResult receivePayResult(DailyTicketPayCallbackReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return fail(result, "orderNo不能为空");
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if ("success".equalsIgnoreCase(request.getPayResult()) || "SUCCESS".equalsIgnoreCase(request.getPayResult())) {
            if (order != null) {
                markPaySuccess(order, request.getTradeNo(), request.getPaymentOrderNo(),
                        request.getPayDate() == null ? new Date() : request.getPayDate(), request.getPayAmount());
            } else {
                TravelTicketOrder travelOrder = travelOrderMapper.selectByOrderNo(request.getOrderNo());
                if (travelOrder == null) {
                    return fail(result, "订单不存在");
                }
                markTravelPaySuccess(travelOrder, request.getTradeNo(), request.getPaymentOrderNo(),
                        request.getPayDate() == null ? new Date() : request.getPayDate(), request.getPayAmount());
            }
        } else {
            if (order != null) {
                markPayFailed(order);
            } else {
                TravelTicketOrder travelOrder = travelOrderMapper.selectByOrderNo(request.getOrderNo());
                if (travelOrder == null) {
                    return fail(result, "订单不存在");
                }
                markTravelPayFailed(travelOrder);
            }
        }
        insertPayLog(request.getOrderNo(), "CALLBACK",
                order == null ? null : order.getPayChannelCode(), request, result);
        return success(result);
    }

    /** 支付中心退款结果回调收口（网关文档 §3.3）。 */
    @Override
    public DailyTicketBaseResult receiveRefundResult(DailyTicketRefundCallbackReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return fail(result, "orderNo不能为空");
        }
        String orderNo = request.getOrderNo();
        DailyTicketOrder order = orderMapper.selectByOrderNo(orderNo);
        DailyTicketRefund refund = refundMapper.selectByOrderNo(orderNo);
        if (refund == null && StringUtils.hasText(request.getOutRefundNo())) {
            refund = refundMapper.selectByRefundOrderNo(request.getOutRefundNo());
        }
        if (refund == null) {
            refund = refundMapper.selectByRefundOrderNo(orderNo);
        }
        TravelTicketOrder travelOrder = refund != null && ORDER_TYPE_TRAVEL_TICKET.equals(refund.getOrderType())
                ? travelOrderMapper.selectByOrderNo(refund.getParentOrderNo() == null ? refund.getOrderNo() : refund.getParentOrderNo())
                : null;
        if ((order == null && travelOrder == null) || refund == null) {
            log.warn("日票退款回调：订单或退款单不存在 orderNo={}, orderExists={}, refundExists={}",
                    orderNo, order != null, refund != null);
            fail(result, "退款记录不存在");
            insertPayLog(orderNo, "REFUND_CALLBACK",
                    order == null ? null : order.getPayChannelCode(), request, result);
            return result;
        }
        String payChannelCode = order == null ? travelOrder.getPayChannelCode() : order.getPayChannelCode();

        String refundStatus = refund.getRefundStatus();
        if ("REFUNDED".equals(refundStatus) || "FAILED".equals(refundStatus)) {
            log.info("日票退款回调重复到达，退款单已是终态，幂等返回 orderNo={}, refundStatus={}", orderNo, refundStatus);
            success(result);
            insertPayLog(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
            return result;
        }
        if (!"REFUNDING".equals(refundStatus) && !"WAIT_VERIFY".equals(refundStatus)) {
            log.warn("日票退款回调：退款单状态不在受理白名单内 orderNo={}, refundStatus={}", orderNo, refundStatus);
            fail(result, "退款单状态不允许收口: " + refundStatus);
            insertPayLog(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
            return result;
        }

        Map<String, Object> refundData = toRefundCallbackData(request);
        String refundResult = request.getRefundResult();
        if ("SUCCESS".equalsIgnoreCase(refundResult)) {
            if (travelOrder != null) {
                markTravelRefunded(travelOrder, refund, refundData);
            } else {
                markRefunded(order, refund, refundData);
            }
            success(result);
        } else if ("FAIL".equalsIgnoreCase(refundResult) || "FAILED".equalsIgnoreCase(refundResult)) {
            if (travelOrder != null) {
                markTravelRefundFailed(travelOrder, refund, refundData);
            } else {
                markRefundFailed(order, refund, refundData);
            }
            success(result);
        } else if ("PROCESSING".equalsIgnoreCase(refundResult)) {
            persistPlatformRefundNoIfChanged(refund, refundData);
            log.info("日票退款回调为处理中，仅回填平台退款单号 orderNo={}, platformRefundNo={}",
                    orderNo, refund.getPlatformRefundNo());
            success(result);
            insertPayLog(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
            return result;
        } else {
            log.warn("日票退款回调：未知的退款结果，不推进状态 orderNo={}, refundResult={}", orderNo, refundResult);
            fail(result, "未知的退款结果: " + refundResult);
            insertPayLog(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
            return result;
        }

        insertPayLog(orderNo, "REFUND_CALLBACK", payChannelCode, request, result);
        refundNotifyService.deliverOne(orderNo);
        return result;
    }

    /** 把回调字段翻译成 {@code markRefunded} / {@code markRefundFailed} 认的 refundData 形状。 */
    private Map<String, Object> toRefundCallbackData(DailyTicketRefundCallbackReqDTO request) {
        Map<String, Object> refundData = new LinkedHashMap<>();
        putIfText(refundData, "refundNo", request.getRefundNo());
        putIfText(refundData, "refundTime", request.getRefundDate());
        return refundData;
    }

    @Override
    public QueryDailyTicketInfoResult queryDailyTicketInfo(QueryDailyTicketInfoReqDTO request) {
        QueryDailyTicketInfoResult result = new QueryDailyTicketInfoResult();
        DailyTicketInstance instance = null;
        if (StringUtils.hasText(request.getCardId())) {
            instance = instanceMapper.selectByCardNum(request.getCardId());
        } else if (StringUtils.hasText(request.getOrderNo())) {
            instance = instanceMapper.selectByOrderNo(request.getOrderNo());
        }
        if (instance == null) {
            result.setRetCode("0000");
            result.setRetMsg("成功");
            return result;
        }
        result.setRetCode("0000");
        result.setRetMsg("成功");
        result.setTicketCode(instance.getTicketCode());
        result.setActualTimes(instance.getActualTimes());
        return result;
    }

    /** 按票号查日票的购票支付信息，供 IF8A-34 / IF8A-05 交易详情填充三个支付字段。 */
    @Override
    public QueryDailyTicketPayInfoResult queryDailyTicketPayInfo(QueryDailyTicketPayInfoReqDTO request) {
        QueryDailyTicketPayInfoResult result = new QueryDailyTicketPayInfoResult();
        if (request == null || !StringUtils.hasText(request.getTicketCode())) {
            return fail(result, "ticketCode不能为空");
        }
        DailyTicketInstance instance = instanceMapper.selectByTicketCode(request.getTicketCode());
        if (instance == null || !StringUtils.hasText(instance.getOrderNo())) {
            log.info("日票支付信息查询：无日票实例, ticketCode={}", request.getTicketCode());
            return success(result);
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(instance.getOrderNo());
        if (order == null) {
            log.warn("日票支付信息查询：实例存在但订单缺失, ticketCode={}, orderNo={}",
                    request.getTicketCode(), instance.getOrderNo());
            return success(result);
        }
        TravelTicketOrder parent = StringUtils.hasText(order.getParentOrderNo())
                ? travelOrderMapper.selectByOrderNo(order.getParentOrderNo())
                : null;
        result.setPayTradeOrderNo(parent == null ? order.getTradeNo() : parent.getTradeNo());
        result.setPayChannelCode(parent == null ? order.getPayChannelCode() : parent.getPayChannelCode());
        Date payDate = parent == null ? order.getPayDate() : parent.getPayDate();
        result.setPayOrderNoDate(payDate == null
                ? null
                : new SimpleDateFormat("yyyyMMddHHmmss").format(payDate));
        return success(result);
    }

    @Override
    public DailyTicketBaseResult validateEntryCheck(String cardNum) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (!StringUtils.hasText(cardNum)) {
            return fail(result, "cardNum不能为空");
        }
        DailyTicketInstance instance = instanceMapper.selectForEntryCheck(cardNum);
        if (instance == null) {
            log.info("日票进站校验：无有效日票实例, cardNum={}", cardNum);
            return fail(result, "无有效日票记录");
        }
        long now = System.currentTimeMillis();
        if (instance.getCountingStart() != null && now < instance.getCountingStart()) {
            log.warn("日票进站校验：未激活, cardNum={}, countingStart={}", cardNum, instance.getCountingStart());
            return fail(result, "日票尚未激活");
        }
        if (instance.getCountingEnd() != null && now > instance.getCountingEnd()) {
            log.warn("日票进站校验：已过期, cardNum={}, countingEnd={}", cardNum, instance.getCountingEnd());
            return fail(result, "日票已过期");
        }
        Integer actualTimes = instance.getActualTimes();
        if (actualTimes != null && actualTimes == 0) {
            log.warn("日票进站校验：计次票次数已用完, cardNum={}", cardNum);
            return fail(result, "计次票次数已用完");
        }
        return success(result);
    }

    /**
     * 拉码（IF8A-03）前置的只读可用性判定。
     * 判据与 {@link #validateEntryCheck} 同源（有效期 / 次数 / 退款占用），但只读、不推进状态，
     * 且异常一律吞掉转成 retCode，因为调用方按「不可达即降级放行」处置。
     */
    @Override
    public DailyTicketBaseResult checkRideAvailability(String cardNum) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        try {
            if (!StringUtils.hasText(cardNum)) {
                return fail(result, "卡号不能为空");
            }
            DailyTicketInstance instance = instanceMapper.selectForEntryCheck(cardNum);
            if (instance == null) {
                log.info("日票可用性校验：无可用日票, cardNum={}", cardNum);
                return fail(result, "无可用日票");
            }
            if (isTicketLockedForRefund(instance.getTicketStatus())) {
                log.warn("日票可用性校验：车票处于退款占用态, cardNum={}, ticketStatus={}",
                        cardNum, instance.getTicketStatus());
                return fail(result, "车票已申请退款，不允许使用");
            }
            long now = System.currentTimeMillis();
            if (instance.getCountingStart() != null && now < instance.getCountingStart()) {
                log.warn("日票可用性校验：未激活, cardNum={}, countingStart={}",
                        cardNum, instance.getCountingStart());
                return fail(result, "日票尚未激活");
            }
            if (instance.getCountingEnd() != null && now > instance.getCountingEnd()) {
                log.warn("日票可用性校验：已过期, cardNum={}, countingEnd={}", cardNum, instance.getCountingEnd());
                return fail(result, "日票已过期");
            }
            Integer actualTimes = instance.getActualTimes();
            if (actualTimes != null && actualTimes == 0) {
                log.warn("日票可用性校验：计次票次数已用完, cardNum={}", cardNum);
                return fail(result, "日票次数已用完");
            }
            log.info("日票可用性校验通过, cardNum={}, ticketStatus={}, actualTimes={}",
                    cardNum, instance.getTicketStatus(), actualTimes);
            return success(result);
        } catch (Exception e) {
            log.error("日票可用性校验异常, cardNum={}", cardNum, e);
            return fail(result, "日票可用性校验异常");
        }
    }

    /** 出站处理：计次票扣次、写入出站时间。 */
    @Override
    public DailyTicketBaseResult markUsed(String cardNum, Long countingEnd) {
        return markUsed(cardNum, countingEnd, null, null, null);
    }

    @Override
    public DailyTicketBaseResult markUsed(String cardNum, Long countingEnd,
                                          String orderNo, String inStation, String outStation) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (!StringUtils.hasText(cardNum)) {
            return fail(result, "cardNum不能为空");
        }
        DailyTicketInstance instance = instanceMapper.selectByCardNum(cardNum);
        if (instance == null) {
            log.warn("日票出站：无有效日票实例, cardNum={}", cardNum);
            return fail(result, "无有效日票记录");
        }
        if (isTicketLockedForRefund(instance.getTicketStatus())) {
            log.warn("日票出站：车票处于退款占用态，拒绝扣次, cardNum={}, ticketStatus={}",
                    cardNum, instance.getTicketStatus());
            return fail(result, "车票已申请退款，不允许使用");
        }
        Date now = new Date();
        Integer actualTimes = instance.getActualTimes();
        int remainTimes = actualTimes == null ? -1 : actualTimes;
        if (actualTimes != null && actualTimes > 0) {
            int updated = instanceMapper.decreaseActualTimes(instance.getId(), now);
            remainTimes = actualTimes - (updated > 0 ? 1 : 0);
            log.info("日票出站：计次票扣次, cardNum={}, 剩余次数={}", cardNum, remainTimes);
        }
        String nextStatus = remainTimes == 0 ? TICKET_STATUS_EXPIRED : TICKET_STATUS_USED;
        instance.setTicketStatus(nextStatus);
        instance.setCountingEnd(countingEnd == null ? instance.getCountingEnd() : countingEnd);
        instance.setFirstUseTime(instance.getFirstUseTime() == null ? now : instance.getFirstUseTime());
        instance.setAccNoticeStatus("SUCCESS");
        instance.setAccNoticeTime(now);
        instance.setUpdateTime(now);
        int marked = instanceMapper.markUsed(instance);
        if (marked == 0) {
            log.error("日票出站：实例状态回写影响 0 行, cardNum={}, instanceId={}", cardNum, instance.getId());
        }

        insertUsageLog(cardNum, orderNo, inStation, outStation, actualTimes, remainTimes, nextStatus);
        refreshTravelParentSummaryBySubOrder(instance.getOrderNo());

        log.info("日票出站处理完成, cardNum={}, ticketStatus={}, 剩余次数={}, countingEnd={}, orderNo={}",
                cardNum, nextStatus, remainTimes, instance.getCountingEnd(), orderNo);
        return success(result);
    }

    /**
     * INSERT 扣次明细，UK_DTUL_ORDER 做幂等。
     * daily-ticket-server 已开 tracing，切面可能把异常包一层，所以沿 cause 链判定。
     * 明细落库失败不中断出站流程，只记 ERROR 留证据。
     */
    private void insertUsageLog(String cardNum, String orderNo, String inStation,
                                String outStation, Integer timesBefore, int timesAfter,
                                String ticketStatus) {
        try {
            DailyTicketUsageLog usageLog = new DailyTicketUsageLog();
            usageLog.setCardNum(cardNum);
            usageLog.setOrderNo(orderNo);
            usageLog.setTxnDate(new SimpleDateFormat("yyyyMMdd").format(new Date()));
            usageLog.setInStation(inStation);
            usageLog.setOutStation(outStation);
            usageLog.setTimesBefore(timesBefore);
            usageLog.setTimesAfter(timesAfter);
            usageLog.setTicketStatus(ticketStatus);
            usageLogMapper.insert(usageLog);
            log.info("日票扣次明细已记录, cardNum={}, orderNo={}, timesBefore={}, timesAfter={}",
                    cardNum, orderNo, timesBefore, timesAfter);
        } catch (Exception e) {
            if (isIntegrityViolation(e)) {
                log.info("日票扣次明细重复插入（幂等命中）, cardNum={}, orderNo={}", cardNum, orderNo);
            } else {
                log.error("日票扣次明细插入失败, cardNum={}, orderNo={}", cardNum, orderNo, e);
            }
        }
    }

    /** 沿 cause 链判定是否为唯一索引冲突（兼容 tracing 切面包装）。 */
    private static boolean isIntegrityViolation(Throwable t) {
        for (Throwable cur = t; cur != null; cur = cur.getCause()) {
            if (cur instanceof org.springframework.dao.DuplicateKeyException
                    || cur instanceof org.springframework.dao.DataIntegrityViolationException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public DailyTicketBaseResult queryUsageLog(String cardNum) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (!StringUtils.hasText(cardNum)) {
            return fail(result, "cardNum不能为空");
        }
        List<DailyTicketUsageLog> logs = usageLogMapper.selectByCardNum(cardNum);
        result.setRetCode(RET_SUCCESS);
        result.setRetMsg("成功，共" + logs.size() + "条");
        result.setData(logs);
        log.info("查询日票扣次明细完成, cardNum={}, count={}", cardNum, logs.size());
        return result;
    }

    private void markPaySuccess(DailyTicketOrder order, String tradeNo, String paymentOrderNo,
                                Date payDate, Integer payAmount) {
        order.setOrderStatus("PAID");
        order.setPayStatus("PAID");
        order.setTradeNo(tradeNo);
        order.setPaymentOrderNo(paymentOrderNo);
        order.setPayAmount(payAmount == null ? order.getTicketPrice() : payAmount);
        order.setPayDate(payDate);
        order.setUpdateTime(new Date());
        updatePayTerminalIfPaying(order);
    }

    private void markPayFailed(DailyTicketOrder order) {
        order.setOrderStatus("PAY_FAILED");
        order.setPayStatus("FAIL");
        order.setUpdateTime(new Date());
        updatePayTerminalIfPaying(order);
    }

    private void updatePayTerminalIfPaying(DailyTicketOrder order) {
        if (orderMapper.updatePayResultIfPaying(order) == 1) {
            return;
        }
        DailyTicketOrder latest = orderMapper.selectByOrderNo(order.getOrderNo());
        if (latest != null && "PAID".equals(latest.getOrderStatus())
                && StringUtils.hasText(order.getPaymentOrderNo())) {
            updatePaymentOrderNo(latest, order.getPaymentOrderNo());
        }
        log.info("日票支付终态重复处理被忽略 orderNo={}, targetOrderStatus={}, targetPayStatus={}, "
                        + "currentOrderStatus={}, currentPayStatus={}",
                order.getOrderNo(), order.getOrderStatus(), order.getPayStatus(),
                latest == null ? null : latest.getOrderStatus(), latest == null ? null : latest.getPayStatus());
    }

    private void queryAndRefreshPayResult(DailyTicketOrder order) {
        Map<String, Object> queryRequest = new LinkedHashMap<>();
        queryRequest.put("merchantOrderNo", order.getOrderNo());
        putIfText(queryRequest, "orderNo", order.getPaymentOrderNo());
        log.info("日票服务准备调用支付网关支付查询接口 orderNo={}, request={}",
                order.getOrderNo(), JSON.toJSONString(queryRequest));
        DailyTicketPayGatewayResponse queryResponse = payGatewayClient.requestPayQuery(queryRequest);
        log.info("日票服务调用支付网关支付查询接口完成 orderNo={}, response={}",
                order.getOrderNo(), JSON.toJSONString(queryResponse));
        insertPayLog(order.getOrderNo(), "QUERY", order.getPayChannelCode(), queryRequest, queryResponse);
        if (!isGatewaySuccess(queryResponse) || queryResponse.getData() == null) {
            return;
        }

        Map<String, Object> data = queryResponse.getData();
        String status = stringValue(data.get("status"), null);
        if (isPaySuccessStatus(status)) {
            markPaySuccess(order,
                    stringValue(data.get("channelOrderNo"), order.getTradeNo()),
                    stringValue(data.get("orderNo"), order.getPaymentOrderNo()),
                    parseGatewayPayDate(stringValue(data.get("payDate"), null)),
                    integerValue(data.get("cashAmount"), integerValue(data.get("totalAmount"), order.getTicketPrice())));
        } else if (isPayFailedStatus(status)) {
            markPayFailed(order);
        }
    }

    private void markTravelPaySuccess(TravelTicketOrder order, String tradeNo, String paymentOrderNo,
                                      Date payDate, Integer payAmount) {
        order.setOrderStatus("PAID");
        order.setPayStatus("PAID");
        order.setTradeNo(tradeNo);
        order.setPaymentOrderNo(paymentOrderNo);
        order.setPayAmount(payAmount == null ? order.getTotalAmount() : payAmount);
        order.setPayDate(payDate);
        order.setUpdateTime(new Date());
        if (travelOrderMapper.updatePayResultIfPaying(order) == 0) {
            TravelTicketOrder latest = travelOrderMapper.selectByOrderNo(order.getOrderNo());
            if (latest != null && "PAID".equals(latest.getOrderStatus())
                    && StringUtils.hasText(order.getPaymentOrderNo())) {
                updateTravelPaymentOrderNo(latest, order.getPaymentOrderNo());
            }
        }
    }

    private void markTravelPayFailed(TravelTicketOrder order) {
        order.setOrderStatus("PAY_FAILED");
        order.setPayStatus("FAIL");
        order.setUpdateTime(new Date());
        travelOrderMapper.updatePayResultIfPaying(order);
    }

    private void queryAndRefreshTravelPayResult(TravelTicketOrder order) {
        Map<String, Object> queryRequest = new LinkedHashMap<>();
        queryRequest.put("merchantOrderNo", order.getOrderNo());
        putIfText(queryRequest, "orderNo", order.getPaymentOrderNo());
        DailyTicketPayGatewayResponse response = payGatewayClient.requestPayQuery(queryRequest);
        insertPayLog(order.getOrderNo(), "QUERY", order.getPayChannelCode(), queryRequest, response);
        if (!isGatewaySuccess(response) || response.getData() == null) {
            return;
        }
        Map<String, Object> data = response.getData();
        String status = stringValue(data.get("status"), null);
        if (isPaySuccessStatus(status)) {
            markTravelPaySuccess(order,
                    stringValue(data.get("channelOrderNo"), order.getTradeNo()),
                    stringValue(data.get("orderNo"), order.getPaymentOrderNo()),
                    parseGatewayPayDate(stringValue(data.get("payDate"), null)),
                    integerValue(data.get("cashAmount"), integerValue(data.get("totalAmount"), order.getTotalAmount())));
        } else if (isPayFailedStatus(status)) {
            markTravelPayFailed(order);
        }
    }

    private boolean isPaySuccessStatus(String status) {
        return "SUCCESS".equalsIgnoreCase(status) || "PAID".equalsIgnoreCase(status)
                || "PAY_SUCCESS".equalsIgnoreCase(status) || "TRADE_SUCCESS".equalsIgnoreCase(status)
                || "1".equals(status);
    }

    private boolean isPayFailedStatus(String status) {
        return "FAIL".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)
                || "PAY_FAILED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)
                || "CANCELED".equalsIgnoreCase(status) || "0".equals(status);
    }

    /** 支付平台退款查询的成功状态兼容不同渠道返回值。 */
    private boolean isRefundSuccessStatus(String status) {
        return "SUCCESS".equalsIgnoreCase(status) || "REFUNDED".equalsIgnoreCase(status)
                || "REFUND_SUCCESS".equalsIgnoreCase(status) || "1".equals(status);
    }

    /** 仅将平台明确的失败终态回写为 FAILED；未知状态继续保持处理中。 */
    private boolean isRefundFailedStatus(String status) {
        return "FAIL".equalsIgnoreCase(status) || "FAILED".equalsIgnoreCase(status)
                || "REFUND_FAILED".equalsIgnoreCase(status) || "CLOSED".equalsIgnoreCase(status)
                || "CANCELED".equalsIgnoreCase(status) || "0".equals(status);
    }

    private Date parseGatewayPayDate(String payDate) {
        if (!StringUtils.hasText(payDate)) {
            return new Date();
        }
        try {
            return new SimpleDateFormat("yyyyMMddHHmmss").parse(payDate);
        } catch (Exception e) {
            log.warn("日票支付查询返回的支付时间格式错误 payDate={}", payDate);
            return new Date();
        }
    }

    /** 将支付平台退款单号、完成结果同步到退款单和原日票订单。 */
    private void markRefunded(DailyTicketOrder order, DailyTicketRefund refund, Map<String, Object> refundData) {
        Date now = new Date();
        updatePlatformRefundNo(refund, refundData);
        refund.setRefundStatus("REFUNDED");
        refund.setRefundDate(parseGatewayRefundDate(stringValue(refundData == null ? null : refundData.get("refundTime"), null), now));
        refund.setUpdateTime(now);
        refundMapper.updateResult(refund);
        orderMapper.updateOrderStatus(order.getOrderNo(), "REFUNDED");
        settleTicketOnRefunded(order.getOrderNo());
        markRefundNotifyPending(refund);
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

    private DailyTicketRefund buildTravelSubRefund(TravelTicketOrder parent, DailyTicketOrder child,
                                                   TravelTicketSubRefundRequest request) {
        Date now = new Date();
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setId(nextId());
        refund.setOrderNo(child.getOrderNo());
        refund.setOrderType(ORDER_TYPE_TRAVEL_TICKET);
        refund.setRefundScope("TRAVEL_SUB");
        refund.setParentOrderNo(parent.getOrderNo());
        refund.setRefundReason(StringUtils.hasText(request.getRefundReason()) ? request.getRefundReason() : "旅游票子单退款");
        refund.setOperator(request.getOperator());
        refund.setRefundOrderNo(buildRefundOrderNo(child.getOrderNo()));
        refund.setRefundAmount(child.getTicketPrice() == null ? 0 : child.getTicketPrice());
        refund.setRefundStatus("REFUNDING");
        refund.setRefundType("00");
        refund.setCreateTime(now);
        refund.setUpdateTime(now);
        return refund;
    }

    private void insertTravelRefundDetails(DailyTicketRefund refund, String parentOrderNo,
                                            List<DailyTicketOrder> children) {
        Date now = new Date();
        if (children == null) {
            return;
        }
        for (DailyTicketOrder child : children) {
            DailyTicketRefundDetail detail = new DailyTicketRefundDetail();
            detail.setId(nextId());
            detail.setRefundOrderNo(refund.getRefundOrderNo());
            detail.setParentOrderNo(parentOrderNo);
            detail.setSubOrderNo(child.getOrderNo());
            detail.setRefundAmount(child.getTicketPrice());
            detail.setRefundStatus("REFUNDING");
            detail.setCreateTime(now);
            detail.setUpdateTime(now);
            refundDetailMapper.insert(detail);
        }
    }

    private Map<String, Object> buildTravelRefundRequest(TravelTicketOrder parent, DailyTicketRefund refund) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("refundOrderNo", refund.getRefundOrderNo());
        request.put("merchantOrderNo", parent.getOrderNo());
        request.put("orderNo", parent.getPaymentOrderNo());
        request.put("refundAmount", refund.getRefundAmount());
        request.put("refundReason", refund.getRefundReason());
        putIfText(request, "notifyUrl", payProperties.getRefundNotifyUrl());
        return request;
    }

    private Map<String, Object> buildTravelSubRefundRequest(TravelTicketOrder parent, DailyTicketRefund refund) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("refundOrderNo", refund.getRefundOrderNo());
        request.put("merchantOrderNo", refund.getOrderNo());
        request.put("orderNo", parent.getPaymentOrderNo());
        request.put("refundAmount", refund.getRefundAmount());
        request.put("refundReason", refund.getRefundReason());
        putIfText(request, "notifyUrl", payProperties.getRefundNotifyUrl());
        return request;
    }

    private void markTravelRefunded(TravelTicketOrder parent, DailyTicketRefund refund,
                                    Map<String, Object> refundData) {
        Date now = new Date();
        updatePlatformRefundNo(refund, refundData);
        refund.setRefundStatus("REFUNDED");
        refund.setRefundDate(parseGatewayRefundDate(
                stringValue(refundData == null ? null : refundData.get("refundTime"), null), now));
        refund.setUpdateTime(now);
        refundMapper.updateResult(refund);
        refundDetailMapper.updateStatusByRefundOrderNo(refund.getRefundOrderNo(), "REFUNDED");
        List<DailyTicketOrder> refundedChildren = new ArrayList<>();
        if ("TRAVEL_SUB".equals(refund.getRefundScope())) {
            DailyTicketOrder child = orderMapper.selectByOrderNo(refund.getOrderNo());
            if (child != null) {
                refundedChildren.add(child);
            }
        } else {
            List<DailyTicketOrder> children = orderMapper.selectByParentOrderNo(parent.getOrderNo());
            if (children != null) {
                refundedChildren.addAll(children);
            }
        }
        for (DailyTicketOrder child : refundedChildren) {
                settleTicketOnRefunded(child.getOrderNo());
        }
        if ("TRAVEL_FULL".equals(refund.getRefundScope())) {
            travelOrderMapper.updateOrderStatus(parent.getOrderNo(), "REFUNDED");
        } else {
            refreshTravelParentSummary(parent.getOrderNo());
        }
        markRefundNotifyPending(refund);
    }

    private void markTravelRefundFailed(TravelTicketOrder parent, DailyTicketRefund refund,
                                        Map<String, Object> refundData) {
        refund.setRefundStatus("FAILED");
        refund.setRefundDate(null);
        refund.setUpdateTime(new Date());
        updatePlatformRefundNo(refund, refundData);
        refundMapper.updateResult(refund);
        refundDetailMapper.updateStatusByRefundOrderNo(refund.getRefundOrderNo(), "FAILED");
        if ("TRAVEL_SUB".equals(refund.getRefundScope())) {
            releaseTicketLock(refund.getOrderNo());
            refreshTravelParentSummary(parent.getOrderNo());
        } else {
            for (DailyTicketOrder child : orderMapper.selectByParentOrderNo(parent.getOrderNo())) {
                releaseTicketLock(child.getOrderNo());
            }
            travelOrderMapper.updateOrderStatus(parent.getOrderNo(), "PAID");
        }
        markRefundNotifyPending(refund);
    }

    private void markTravelRefunding(String parentOrderNo, DailyTicketRefund refund) {
        refund.setRefundStatus("REFUNDING");
        refund.setRefundDate(null);
        refund.setUpdateTime(new Date());
        refundMapper.updateResult(refund);
        travelOrderMapper.updateOrderStatus(parentOrderNo, "REFUNDING");
    }

    private void refreshTravelParentSummaryBySubOrder(String subOrderNo) {
        DailyTicketOrder subOrder = orderMapper.selectByOrderNo(subOrderNo);
        if (subOrder == null || !StringUtils.hasText(subOrder.getParentOrderNo())) {
            return;
        }
        refreshTravelParentSummary(subOrder.getParentOrderNo());
    }

    private void refreshTravelParentSummary(String parentOrderNo) {
        TravelTicketOrder parent = travelOrderMapper.selectByOrderNo(parentOrderNo);
        if (parent == null || !"PAID".equals(parent.getPayStatus())) {
            return;
        }
        List<DailyTicketOrder> children = orderMapper.selectByParentOrderNo(parentOrderNo);
        if (children == null || children.isEmpty()) {
            return;
        }
        int used = 0;
        int refunded = 0;
        for (DailyTicketOrder child : children) {
            DailyTicketInstance ticket = instanceMapper.selectByOrderNo(child.getOrderNo());
            if (ticket != null && (TICKET_STATUS_USED.equals(ticket.getTicketStatus())
                    || TICKET_STATUS_EXPIRED.equals(ticket.getTicketStatus()))) {
                used++;
            }
            DailyTicketRefund childRefund = refundMapper.selectByOrderNo(child.getOrderNo());
            if (childRefund != null && "REFUNDED".equals(childRefund.getRefundStatus())) {
                refunded++;
            }
        }
        String summaryStatus = "PAID";
        if (refunded == children.size()) {
            summaryStatus = "REFUNDED";
        } else if (refunded > 0) {
            summaryStatus = "PARTIAL_REFUNDED";
        } else if (used == children.size()) {
            summaryStatus = "USED";
        } else if (used > 0) {
            summaryStatus = "PARTIAL_USED";
        }
        travelOrderMapper.updateOrderStatus(parentOrderNo, summaryStatus);
    }

    /** 明确失败时保留原退款单，后续重试必须继续使用该退款单号。 */
    private void markRefundFailed(DailyTicketOrder order, DailyTicketRefund refund, Map<String, Object> refundData) {
        Date now = new Date();
        updatePlatformRefundNo(refund, refundData);
        refund.setRefundStatus("FAILED");
        refund.setRefundDate(null);
        refund.setUpdateTime(now);
        refundMapper.updateResult(refund);
        orderMapper.updateOrderStatus(order.getOrderNo(), "PAID");
        releaseTicketLock(order.getOrderNo());
        markRefundNotifyPending(refund);
    }

    /**
     * 发起核验退款时把票从 {@code ACTIVATED} 锁进 {@code REFUND_LOCKED}，返回是否抢到。
     * 这是本模块唯一阻止「观察期内继续乘坐」的地方：锁上之后 {@code selectForEntryCheck} 查不到、
     * {@code markUsed} 也会被 {@link #isTicketLockedForRefund} 挡住。
     */
    private boolean lockTicketForRefund(DailyTicketInstance ticket) {
        return instanceMapper.updateStatusIfCurrent(ticket.getId(),
                TICKET_STATUS_ACTIVATED, TICKET_STATUS_REFUND_LOCKED, new Date()) > 0;
    }

    /**
     * 放款成功后把票推进终态 {@code REFUNDED}。
     * 未激活票（refundType=00）在本表没有实例、CAS 影响 0 行，属正常，只记日志不报错。
     */
    private void settleTicketOnRefunded(String orderNo) {
        DailyTicketInstance ticket = instanceMapper.selectByOrderNo(orderNo);
        if (ticket == null) {
            return;
        }
        int settled = instanceMapper.updateStatusIfCurrent(ticket.getId(),
                TICKET_STATUS_REFUND_LOCKED, TICKET_STATUS_REFUNDED, new Date());
        if (settled == 0) {
            log.error("日票退款已放款但票状态不是 REFUND_LOCKED，需人工核对 orderNo={}, instanceId={}, ticketStatus={}",
                    orderNo, ticket.getId(), ticket.getTicketStatus());
        }
    }

    /** 退款明确失败后把票解锁回 {@code ACTIVATED}，否则用户既没退到钱、票也被锁死。 */
    private void releaseTicketLock(String orderNo) {
        DailyTicketInstance ticket = instanceMapper.selectByOrderNo(orderNo);
        if (ticket == null) {
            return;
        }
        instanceMapper.updateStatusIfCurrent(ticket.getId(),
                TICKET_STATUS_REFUND_LOCKED, TICKET_STATUS_ACTIVATED, new Date());
    }

    /** 票是否处于退款占用态：{@code REFUND_LOCKED} 观察期内或 {@code REFUNDED} 已放款。 */
    private boolean isTicketLockedForRefund(String ticketStatus) {
        return TICKET_STATUS_REFUND_LOCKED.equals(ticketStatus) || TICKET_STATUS_REFUNDED.equals(ticketStatus);
    }

    /** 退款进入终态后把 IF8B-04 通知置为待发。 */
    private void markRefundNotifyPending(DailyTicketRefund refund) {
        refund.setNotifyStatus("PENDING");
        refund.setNotifyTimes(0);
        refundMapper.updateNotifyStatus(refund.getOrderNo(), "PENDING", 0, null, null);
    }

    /** 重试提交成功后回到处理中，等待支付平台异步或人工查询结果。 */
    private void markRefunding(DailyTicketOrder order, DailyTicketRefund refund) {
        Date now = new Date();
        refund.setRefundStatus("REFUNDING");
        refund.setRefundDate(null);
        refund.setUpdateTime(now);
        refundMapper.updateResult(refund);
        orderMapper.updateOrderStatus(order.getOrderNo(), "REFUNDING");
    }

    /** 支付平台版本的字段名存在 refundOrderNo/refundNo 两种实现，优先读取文档字段，兼容旧实现。 */
    private void updatePlatformRefundNo(DailyTicketRefund refund, Map<String, Object> refundData) {
        if (refundData == null) {
            return;
        }
        String platformRefundNo = stringValue(refundData.get("refundOrderNo"), null);
        if (!StringUtils.hasText(platformRefundNo)) {
            platformRefundNo = stringValue(refundData.get("refundNo"), null);
        }
        if (StringUtils.hasText(platformRefundNo)) {
            refund.setPlatformRefundNo(platformRefundNo);
        }
    }

    /**
     * 对旧退款数据从最近一笔退款网关响应中恢复平台退款单号。
     * 恢复失败时不降级为单字段查询，防止错误关联到其他退款单。
     */
    private void restorePlatformRefundNoFromPayLog(String orderNo, DailyTicketRefund refund) {
        if (StringUtils.hasText(refund.getPlatformRefundNo())) {
            return;
        }
        String responseBody = payLogMapper.selectLatestRefundResponseBody(orderNo);
        if (!StringUtils.hasText(responseBody)) {
            return;
        }
        try {
            DailyTicketPayGatewayResponse response = JSON.parseObject(responseBody, DailyTicketPayGatewayResponse.class);
            if (response == null || response.getData() == null) {
                return;
            }
            persistPlatformRefundNoIfChanged(refund, response.getData());
        } catch (Exception e) {
            log.warn("日票退款历史网关响应解析失败，无法恢复平台退款单号 orderNo={}", orderNo, e);
        }
    }

    /** 仅在响应实际带回新平台退款单号时更新，避免查询处理中覆盖退款完成时间。 */
    private void persistPlatformRefundNoIfChanged(DailyTicketRefund refund, Map<String, Object> refundData) {
        String before = refund.getPlatformRefundNo();
        updatePlatformRefundNo(refund, refundData);
        if (!StringUtils.hasText(refund.getPlatformRefundNo()) || refund.getPlatformRefundNo().equals(before)) {
            return;
        }
        refund.setUpdateTime(new Date());
        refundMapper.updateResult(refund);
    }

    private Date parseGatewayRefundDate(String refundTime, Date defaultValue) {
        if (!StringUtils.hasText(refundTime)) {
            return defaultValue;
        }
        try {
            return new SimpleDateFormat("yyyyMMddHHmmss").parse(refundTime);
        } catch (Exception e) {
            log.warn("日票退款查询返回的退款时间格式错误 refundTime={}", refundTime);
            return defaultValue;
        }
    }

    private Integer integerValue(Object value, Integer defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private DailyTicketRefund buildRefund(DailyTicketOrder order, String refundType) {
        Date now = new Date();
        DailyTicketRefund refund = new DailyTicketRefund();
        refund.setId(nextId());
        refund.setOrderNo(order.getOrderNo());
        refund.setRefundOrderNo(buildRefundOrderNo(order.getOrderNo()));
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

    private Map<String, Object> buildTravelTicketPayRequest(TravelTicketOrder order, DailyTicketPayReqDTO request) {
        Map<String, Object> payRequest = new LinkedHashMap<>();
        payRequest.put("orderNo", order.getOrderNo());
        payRequest.put("scene", order.getPayScene());
        payRequest.put("paymentVendor", request.getPayChannelCode());
        payRequest.put("amount", order.getTotalAmount());
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
        refundRequest.put("refundOrderNo", refund.getRefundOrderNo());
        refundRequest.put("merchantOrderNo", order.getOrderNo());
        refundRequest.put("orderNo", order.getPaymentOrderNo());
        refundRequest.put("refundAmount", refund.getRefundAmount());
        refundRequest.put("refundReason", "日票退款");
        putIfText(refundRequest, "notifyUrl", payProperties.getRefundNotifyUrl());
        return refundRequest;
    }

    private void updatePaymentOrderNo(DailyTicketOrder order, String paymentOrderNo) {
        if (!StringUtils.hasText(paymentOrderNo)) {
            return;
        }
        orderMapper.updatePaymentOrderNo(order.getOrderNo(), paymentOrderNo);
        if (!StringUtils.hasText(order.getPaymentOrderNo())) {
            order.setPaymentOrderNo(paymentOrderNo);
        }
    }

    private void updateTravelPaymentOrderNo(TravelTicketOrder order, String paymentOrderNo) {
        if (!StringUtils.hasText(paymentOrderNo)) {
            return;
        }
        travelOrderMapper.updatePaymentOrderNo(order.getOrderNo(), paymentOrderNo);
        if (!StringUtils.hasText(order.getPaymentOrderNo())) {
            order.setPaymentOrderNo(paymentOrderNo);
        }
    }

    private String buildRefundOrderNo(String orderNo) {
        String suffix = orderNo;
        if (suffix != null && suffix.length() > 8) {
            suffix = suffix.substring(suffix.length() - 8);
        }
        return "RF" + new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date())
                + (suffix == null ? "" : suffix);
    }

    private DailyTicketRefundResult buildExistingRefundResult(DailyTicketRefundResult result,
                                                               DailyTicketRefund refund) {
        result.setRefundType(refund.getRefundType());
        result.setOrderNo(refund.getRefundOrderNo());
        result.setRefundAmount(String.valueOf(refund.getRefundAmount() == null ? 0 : refund.getRefundAmount()));
        result.setRefundDate(refund.getRefundDate() == null ? null
                : new SimpleDateFormat("yyyyMMddHHmmss").format(refund.getRefundDate()));
        if ("REFUNDED".equals(refund.getRefundStatus())) {
            result.setRefundResult("SUCCESS");
            result.setRefundResultDesc("退款已完成");
        } else if ("FAILED".equals(refund.getRefundStatus())) {
            result.setRefundResult("FAILED");
            result.setRefundResultDesc("退款失败，可发起退款重试");
        } else {
            result.setRefundResult("PROCESSING");
            result.setRefundResultDesc("退款已申请，请勿重复提交");
        }
        return success(result);
    }

    private int safePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int safePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    private boolean isGatewaySuccess(DailyTicketPayGatewayResponse response) {
        return response != null && (Boolean.TRUE.equals(response.getSuccess())
                || Integer.valueOf(0).equals(response.getCode())
                || Integer.valueOf(200).equals(response.getCode()));
    }

    private boolean isGatewayExplicitFailure(DailyTicketPayGatewayResponse response) {
        if (response == null) {
            return false;
        }
        if (Boolean.FALSE.equals(response.getSuccess())) {
            return true;
        }
        return response.getCode() != null && !Integer.valueOf(0).equals(response.getCode())
                && !Integer.valueOf(200).equals(response.getCode());
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

    /** 旅游票主单号，前缀 0T 与日票子单的 0E 区分，便于日志与运营侧一眼分辨聚合单。 */
    private String nextTravelOrderNo() {
        return "0T" + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%04d", orderSequence.getAndIncrement() % 10000);
    }

    private TravelTicketOrder buildTravelMainOrder(TravelTicketOrderReqDTO request, String travelOrderNo,
                                                   int ticketPrice, int ticketCount, Date now) {
        TravelTicketOrder main = new TravelTicketOrder();
        main.setId(nextId());
        main.setOrderNo(travelOrderNo);
        main.setUserId(request.getUserId());
        main.setCardType(request.getCardType());
        main.setShowType(request.getShowType());
        main.setTicketPrice(ticketPrice);
        main.setTicketCount(ticketCount);
        main.setTotalAmount(request.getTotalAmount());
        main.setOrderSource(request.getOrderSource());
        main.setOrderStatus("CREATED");
        main.setPayStatus("INIT");
        main.setCreateTime(now);
        main.setUpdateTime(now);
        return main;
    }

    private DailyTicketOrder buildTravelSubOrder(TravelTicketOrderReqDTO request, String travelOrderNo,
                                                 int ticketPrice, Date now) {
        DailyTicketOrder sub = new DailyTicketOrder();
        sub.setId(nextId());
        sub.setOrderNo(nextOrderNo());
        sub.setOrderType(ORDER_TYPE_DAILY_TICKET);
        sub.setParentOrderNo(travelOrderNo);
        sub.setOrderSource(request.getOrderSource());
        sub.setTicketPrice(ticketPrice);
        sub.setCardType(request.getCardType());
        sub.setShowType(request.getShowType());
        sub.setUserId(request.getUserId());
        sub.setOrderStatus("CREATED");
        sub.setPayStatus("INIT");
        sub.setCreateTime(now);
        sub.setUpdateTime(now);
        return sub;
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

    private String validateTravelOrderRequest(TravelTicketOrderReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        if (!StringUtils.hasText(request.getUserId())) {
            return "userId不能为空";
        }
        if (request.getTicketCount() == null || request.getTicketCount() <= 0) {
            return "ticketCount必须大于0";
        }
        if (request.getTicketCount() > MAX_TRAVEL_TICKET_COUNT) {
            return "ticketCount不能超过" + MAX_TRAVEL_TICKET_COUNT;
        }
        if (request.getTotalAmount() == null || request.getTotalAmount() <= 0) {
            return "totalAmount必须大于0";
        }
        if (request.getTotalAmount() % request.getTicketCount() != 0) {
            return "totalAmount必须能被ticketCount整除";
        }
        return null;
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
        if (!ORDER_TYPE_DAILY_TICKET.equals(orderType)
                && !ORDER_TYPE_TRAVEL_TICKET.equals(orderType)) {
            return "orderType必须为1或2";
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
