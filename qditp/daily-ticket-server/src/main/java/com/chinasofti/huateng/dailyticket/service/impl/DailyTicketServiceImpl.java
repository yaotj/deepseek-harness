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
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundOrderView;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundQuery;
import com.chinasofti.huateng.dailyticket.page.DailyTicketRefundView;
import com.chinasofti.huateng.dailyticket.service.DailyTicketService;
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
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketUsedNoticeReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(DailyTicketServiceImpl.class);

    private static final String RET_SUCCESS = "0000";
    private static final String RET_FAIL = "9999";
    private static final String ORDER_TYPE_DAILY_TICKET = "1";

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
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        // 即使本地仍是支付中，也以支付平台查询结果为准刷新订单状态。
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
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "订单不存在");
        }
        // 同一日票订单只生成一笔退款单；重复请求直接返回已有处理结果。
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
        if (ticket != null && "USED".equals(ticket.getTicketStatus())) {
            return fail(result, "车票已使用，不允许退款");
        }

        // 未激活票可直退；已激活但尚未使用的票进入后续人工/定时核验流程。
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
        log.info("日票服务准备调用支付网关退款接口 orderNo={}, request={}", order.getOrderNo(), JSON.toJSONString(refundReq));
        DailyTicketPayGatewayResponse refundResponse = payGatewayClient.requestRefund(refundReq);
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
            // 网关仅确认受理时不能直接标为完成，保存平台退款单号后等待结果查询。
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

    @Override
    public DailyTicketRefundResult queryRefundTicket(DailyTicketOrderNoReqDTO request) {
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
        if (!"REFUNDING".equals(refund.getRefundStatus()) && !"FAILED".equals(refund.getRefundStatus())) {
            return buildExistingRefundResult(result, refund);
        }

        // 两个定位字段必须同时传入。历史数据先从原退款网关响应补齐平台退款单号。
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

        // 查询响应可能第一次返回平台退款单号，先落库确保后续查询仍携带双字段。
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

    @Override
    public DailyTicketRefundResult retryRefundTicket(DailyTicketOrderNoReqDTO request) {
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
        if (!"00".equals(refund.getRefundType())) {
            return fail(result, "核验退款不支持支付平台重试");
        }
        if (!"REFUNDING".equals(refund.getRefundStatus()) && !"FAILED".equals(refund.getRefundStatus())) {
            return buildExistingRefundResult(result, refund);
        }

        // 重试前先查询，避免上一笔请求已经在支付平台成功但本地尚未更新。
        DailyTicketRefundResult queryResult = queryRefundTicket(request);
        if (!RET_SUCCESS.equals(queryResult.getRetCode())
                || (!"PROCESSING".equals(queryResult.getRefundResult()) && !"FAILED".equals(queryResult.getRefundResult()))) {
            return queryResult;
        }

        // 使用已有退款单号重发，支付平台可按 refundOrderNo 保证外部幂等。
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

        // 与首次退款一致，网关同步成功时直接落终态；其余场景保持处理中等待查询。
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

    @Override
    public ResultVO<PageInfo<DailyTicketRefundOrderView>> pageRefundOrders(DailyTicketRefundOrderQuery query) {
        DailyTicketRefundOrderQuery safeQuery = query == null ? new DailyTicketRefundOrderQuery() : query;
        // 统一收敛分页参数，避免无效页码和超大页造成数据库压力。
        PageInfo<DailyTicketRefundOrderView> pageInfo = PageHelper
                .startPage(safePageNum(safeQuery.getPageNum()), safePageSize(safeQuery.getPageSize()))
                .doSelectPageInfo(() -> orderMapper.selectRefundOrders(safeQuery));
        return ResultMapper.ok(pageInfo);
    }

    @Override
    public ResultVO<PageInfo<DailyTicketRefundView>> pageRefundRecords(DailyTicketRefundQuery query) {
        DailyTicketRefundQuery safeQuery = query == null ? new DailyTicketRefundQuery() : query;
        // 退款记录与订单检索分开分页，页面可独立追踪核验退款的后续状态。
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
            markPaySuccess(order, request.getTradeNo(), request.getPaymentOrderNo(),
                    request.getPayDate() == null ? new Date() : request.getPayDate(), request.getPayAmount());
        } else {
            markPayFailed(order);
        }
        insertPayLog(order.getOrderNo(), "CALLBACK", order.getPayChannelCode(), request, result);
        return success(result);
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

    @Override
    public DailyTicketBaseResult validateEntryCheck(String cardNum) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (!StringUtils.hasText(cardNum)) {
            return fail(result, "cardNum不能为空");
        }
        DailyTicketInstance instance = instanceMapper.selectForEntryCheck(cardNum);
        if (instance == null) {
            // 无有效日票实例，返回失败但允许闸机走常规流程
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
        // 计次票次数检查（仅校验，不扣减；扣减在出站时执行）
        Integer actualTimes = instance.getActualTimes();
        if (actualTimes != null && actualTimes <= 0) {
            log.warn("日票进站校验：计次票次数已用完, cardNum={}", cardNum);
            return fail(result, "计次票次数已用完");
        }
        return success(result);
    }

    @Override
    public DailyTicketBaseResult markUsed(String cardNum, Long countingEnd) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (!StringUtils.hasText(cardNum)) {
            return fail(result, "cardNum不能为空");
        }
        DailyTicketInstance instance = instanceMapper.selectByCardNum(cardNum);
        if (instance == null) {
            log.warn("日票出站：无有效日票实例, cardNum={}", cardNum);
            return fail(result, "无有效日票记录");
        }
        Date now = new Date();
        // 计次票扣减一次次数（atomic，下限为0）
        if (instance.getActualTimes() != null && instance.getActualTimes() > 0) {
            int updated = instanceMapper.decreaseActualTimes(cardNum, now);
            log.info("日票出站：计次票扣次, cardNum={}, 剩余次数={}", cardNum,
                    instance.getActualTimes() - (updated > 0 ? 1 : 0));
        }
        // 标记已使用
        instance.setTicketStatus("USED");
        instance.setCountingEnd(countingEnd);
        instance.setFirstUseTime(now);
        instance.setAccNoticeStatus("SUCCESS");
        instance.setAccNoticeTime(now);
        instance.setUpdateTime(now);
        instanceMapper.markUsed(instance);
        log.info("日票出站：标记已使用, cardNum={}, countingEnd={}", cardNum, countingEnd);
        return success(result);
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
        // A duplicate success callback may carry the platform order number that
        // was missing from the first terminal update. Keep it for refunds.
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

    /**
     * 支付平台版本的字段名存在 refundOrderNo/refundNo 两种实现，优先读取文档字段，兼容旧实现。
     */
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

    private Map<String, Object> buildDailyTicketRefundRequest(DailyTicketOrder order, DailyTicketRefund refund) {
        Map<String, Object> refundRequest = new LinkedHashMap<>();
        refundRequest.put("refundOrderNo", refund.getRefundOrderNo());
        refundRequest.put("merchantOrderNo", order.getOrderNo());
        refundRequest.put("orderNo", order.getPaymentOrderNo());
        refundRequest.put("refundAmount", refund.getRefundAmount());
        refundRequest.put("refundReason", "日票退款");
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
            // 处理中和待核验以退款单为最终处理依据，禁止创建新的退款单。
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
