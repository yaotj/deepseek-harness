package com.chinasofti.huateng.dailyticket.service.payment;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayClient;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.config.DailyTicketPayProperties;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketInstanceMapper;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketInstance;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.DailyTicketPayResultNotifyService;
import com.chinasofti.huateng.dailyticket.service.paylog.DailyTicketPayLogWriter;
import com.chinasofti.huateng.dailyticket.service.refund.CanceledOrderRefundService;
import com.chinasofti.huateng.dailyticket.service.refund.DailyTicketRefundMessages;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketPaidFields;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketPayGatewaySupport;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayCallbackReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayQueryResult;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketPayResult;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketPayInfoResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DailyTicketPaymentService {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketPaymentService.class);

    private static final String ORDER_TYPE_TRAVEL_TICKET = DailyTicketOrderSupport.ORDER_TYPE_TRAVEL_TICKET;
    private static final String ORDER_STATUS_CANCELED = "CANCELED";

    private final DailyTicketPayGatewayClient payGatewayClient;
    private final DailyTicketPayProperties payProperties;
    private final DailyTicketOrderMapper orderMapper;
    private final TravelTicketOrderMapper travelOrderMapper;
    private final DailyTicketInstanceMapper instanceMapper;
    private final DailyTicketPayLogWriter payLogWriter;
    private final DailyTicketPayResultNotifyService payResultNotifyService;
    private final CanceledOrderRefundService canceledOrderRefundService;

    public DailyTicketPaymentService(DailyTicketPayGatewayClient payGatewayClient,
                                     DailyTicketPayProperties payProperties,
                                     DailyTicketOrderMapper orderMapper,
                                     TravelTicketOrderMapper travelOrderMapper,
                                     DailyTicketInstanceMapper instanceMapper,
                                     DailyTicketPayLogWriter payLogWriter,
                                     DailyTicketPayResultNotifyService payResultNotifyService,
                                     CanceledOrderRefundService canceledOrderRefundService) {
        this.payGatewayClient = payGatewayClient;
        this.payProperties = payProperties;
        this.orderMapper = orderMapper;
        this.travelOrderMapper = travelOrderMapper;
        this.instanceMapper = instanceMapper;
        this.payLogWriter = payLogWriter;
        this.payResultNotifyService = payResultNotifyService;
        this.canceledOrderRefundService = canceledOrderRefundService;
    }

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
        if (isExternalNoGatewayOrderSource(order.getOrderSource())) {
            return fail(result, "订单来源不支持ITP支付");
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

        Map<String, Object> payRequest = buildPayRequest(order.getOrderNo(), order.getPayScene(),
                order.getTicketPrice(), request);
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

    private DailyTicketPayResult requestTravelPay(DailyTicketPayReqDTO request) {
        DailyTicketPayResult result = new DailyTicketPayResult();
        TravelTicketOrder order = travelOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return fail(result, "旅游票主订单不存在");
        }
        if (isExternalNoGatewayOrderSource(order.getOrderSource())) {
            return fail(result, "订单来源不支持ITP支付");
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

        Map<String, Object> payRequest = buildPayRequest(order.getOrderNo(), order.getPayScene(),
                order.getTotalAmount(), request);
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

    /**
     * 支付中心支付结果回调。
     *
     * <p><b>已取消订单收到支付成功通知时不能走普通成功分支</b>：{@code markPaySuccess} 的 CAS
     * 要求 {@code PAYING + PAYING}，对 {@code CANCELED} 的单一律 0 行、只留一行「重复处理被忽略」日志，
     * 结果是**钱收了、票已取消、没有人退款**。因此这里按订单状态分两支，取消态走
     * 「补支付事实 + 自动退款」（{@code CanceledOrderRefundService}），**NEVER 合并回一支**。
     *
     * <p>支付失败分支不需要特判：{@code markPayFailed} 的 CAS 同样只认 {@code PAYING}，
     * 对取消单落 0 行即可 —— 钱没收到，本来就无事可做。
     */
    public DailyTicketBaseResult receivePayResult(DailyTicketPayCallbackReqDTO request) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return fail(result, "orderNo不能为空");
        }
        DailyTicketOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if ("success".equalsIgnoreCase(request.getPayResult()) || "SUCCESS".equalsIgnoreCase(request.getPayResult())) {
            if (order != null) {
                if (ORDER_STATUS_CANCELED.equals(order.getOrderStatus())) {
                    markPaySuccessOnCanceled(order, request.getTradeNo(), request.getPaymentOrderNo(),
                            request.getPayDate() == null ? new Date() : request.getPayDate(), request.getPayAmount(),
                            request.getCashAmount(), request.getCouponAmount());
                } else {
                    markPaySuccess(order, request.getTradeNo(), request.getPaymentOrderNo(),
                            request.getPayDate() == null ? new Date() : request.getPayDate(), request.getPayAmount(),
                            request.getCashAmount(), request.getCouponAmount());
                }
                payResultNotifyService.enqueueAndDeliver(order, "SUCCESS");
            } else {
                TravelTicketOrder travelOrder = travelOrderMapper.selectByOrderNo(request.getOrderNo());
                if (travelOrder == null) {
                    return fail(result, "订单不存在");
                }
                if (ORDER_STATUS_CANCELED.equals(travelOrder.getOrderStatus())) {
                    markTravelPaySuccessOnCanceled(travelOrder, request.getTradeNo(), request.getPaymentOrderNo(),
                            request.getPayDate() == null ? new Date() : request.getPayDate(), request.getPayAmount(),
                            request.getCashAmount(), request.getCouponAmount());
                } else {
                    markTravelPaySuccess(travelOrder, request.getTradeNo(), request.getPaymentOrderNo(),
                            request.getPayDate() == null ? new Date() : request.getPayDate(), request.getPayAmount(),
                            request.getCashAmount(), request.getCouponAmount());
                }
                payResultNotifyService.enqueueAndDeliver(travelOrder, "SUCCESS");
            }
        } else {
            if (order != null) {
                markPayFailed(order);
                payResultNotifyService.enqueueAndDeliver(order, "FAIL");
            } else {
                TravelTicketOrder travelOrder = travelOrderMapper.selectByOrderNo(request.getOrderNo());
                if (travelOrder == null) {
                    return fail(result, "订单不存在");
                }
                markTravelPayFailed(travelOrder);
                payResultNotifyService.enqueueAndDeliver(travelOrder, "FAIL");
            }
        }
        insertPayLog(request.getOrderNo(), "CALLBACK",
                order == null ? null : order.getPayChannelCode(), request, result);
        return success(result);
    }

    /**
     * 已取消的日票订单收到支付成功通知：只补支付事实（{@code ORDER_STATUS} 保持 {@code CANCELED}），
     * 随后转自动退款。
     *
     * <p>CAS 落 0 行（重复回调）时**仍要调一次退款**，NEVER 直接 return：首次回调可能已经落了
     * 支付事实但退款那一步炸了，靠上游重推补偿是唯一出口；退款服务自身对「已有退款单」幂等短路。
     */
    private void markPaySuccessOnCanceled(DailyTicketOrder order, String tradeNo, String paymentOrderNo,
                                          Date payDate, Integer payAmount, Integer cashAmount, Integer couponAmount) {
        order.setTradeNo(tradeNo);
        order.setPaymentOrderNo(paymentOrderNo);
        order.setPayAmount(payAmount);
        order.setPayDate(payDate);
        order.setCashAmount(cashAmount);
        order.setCouponAmount(couponAmount);
        order.setUpdateTime(new Date());
        int affected = orderMapper.updatePayResultIfCanceled(order);
        log.warn("已取消日票订单收到支付成功通知，转自动退款 orderNo={}, paySnapshotUpdated={}",
                order.getOrderNo(), affected == 1);
        canceledOrderRefundService.refundCanceledDailyOrder(order.getOrderNo());
    }

    /** 已取消的旅游票主单收到支付成功通知，口径与 {@link #markPaySuccessOnCanceled} 一致。 */
    private void markTravelPaySuccessOnCanceled(TravelTicketOrder order, String tradeNo, String paymentOrderNo,
                                                Date payDate, Integer payAmount, Integer cashAmount,
                                                Integer couponAmount) {
        order.setTradeNo(tradeNo);
        order.setPaymentOrderNo(paymentOrderNo);
        order.setPayAmount(payAmount);
        order.setPayDate(payDate);
        order.setCashAmount(cashAmount);
        order.setCouponAmount(couponAmount);
        order.setUpdateTime(new Date());
        int affected = travelOrderMapper.updatePayResultIfCanceled(order);
        log.warn("已取消旅游票主单收到支付成功通知，转自动整单退款 orderNo={}, paySnapshotUpdated={}",
                order.getOrderNo(), affected == 1);
        canceledOrderRefundService.refundCanceledTravelOrder(order.getOrderNo());
    }

    /** 按票号查日票的购票支付信息，供 IF8A-34 / IF8A-05 交易详情填充三个支付字段。 */
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

    private void markPaySuccess(DailyTicketOrder order, String tradeNo, String paymentOrderNo,
                                Date payDate, Integer payAmount, Integer cashAmount, Integer couponAmount) {
        DailyTicketPaidFields.applyPaid(order, tradeNo, paymentOrderNo, payAmount, payDate);
        order.setCashAmount(cashAmount);
        order.setCouponAmount(couponAmount);
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

    public void queryAndRefreshPayResult(DailyTicketOrder order) {
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
                    integerValue(data.get("cashAmount"), integerValue(data.get("totalAmount"), order.getTicketPrice())),
                    integerValue(data.get("cashAmount"), null),
                    integerValue(data.get("couponAmount"), null));
        } else if (isPayFailedStatus(status)) {
            markPayFailed(order);
        }
    }

    private void markTravelPaySuccess(TravelTicketOrder order, String tradeNo, String paymentOrderNo,
                                      Date payDate, Integer payAmount, Integer cashAmount, Integer couponAmount) {
        DailyTicketPaidFields.applyPaid(order, tradeNo, paymentOrderNo, payAmount, payDate);
        order.setCashAmount(cashAmount);
        order.setCouponAmount(couponAmount);
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
        if (travelOrderMapper.updatePayResultIfPaying(order) == 1) {
            return;
        }
        TravelTicketOrder latest = travelOrderMapper.selectByOrderNo(order.getOrderNo());
        log.info("旅游票支付终态重复处理被忽略 orderNo={}, targetOrderStatus={}, targetPayStatus={}, "
                        + "currentOrderStatus={}, currentPayStatus={}",
                order.getOrderNo(), order.getOrderStatus(), order.getPayStatus(),
                latest == null ? null : latest.getOrderStatus(), latest == null ? null : latest.getPayStatus());
    }

    public void queryAndRefreshTravelPayResult(TravelTicketOrder order) {
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
                    integerValue(data.get("cashAmount"), integerValue(data.get("totalAmount"), order.getTotalAmount())),
                    integerValue(data.get("cashAmount"), null),
                    integerValue(data.get("couponAmount"), null));
        } else if (isPayFailedStatus(status)) {
            markTravelPayFailed(order);
        }
    }

    private boolean isPaySuccessStatus(String status) {
        return DailyTicketPayGatewaySupport.isPaySuccessStatus(status);
    }

    private boolean isPayFailedStatus(String status) {
        return DailyTicketPayGatewaySupport.isPayFailedStatus(status);
    }

    private Date parseGatewayPayDate(String payDate) {
        return DailyTicketPayGatewaySupport.parseGatewayPayDate(payDate);
    }

    private Integer integerValue(Object value, Integer defaultValue) {
        return DailyTicketPayGatewaySupport.integerValue(value, defaultValue);
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

    private Map<String, Object> buildPayRequest(String orderNo, String payScene, Integer amount,
                                                DailyTicketPayReqDTO request) {
        Map<String, Object> payRequest = new LinkedHashMap<>();
        payRequest.put("orderNo", orderNo);
        payRequest.put("scene", payScene);
        payRequest.put("paymentVendor", request.getPayChannelCode());
        payRequest.put("amount", amount);
        payRequest.put("industryType", payProperties.getIndustryType());
        payRequest.put("subject", payProperties.getSubject());
        payRequest.put("body", payProperties.getBody());
        payRequest.put("thirdUserId", request.getThirdUserId());
        payRequest.put("phone", request.getPhone());
        putIfText(payRequest, "notifyUrl", payProperties.getNotifyUrl());
        putIfText(payRequest, "returnUrl", payProperties.getReturnUrl());
        return payRequest;
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

    private boolean isGatewayExplicitFailure(DailyTicketPayGatewayResponse response) {
        return DailyTicketRefundMessages.isGatewayExplicitFailure(response);
    }

    private void insertPayLog(String orderNo, String bizType, String payChannelCode, Object request, Object response) {
        payLogWriter.insert(orderNo, bizType, payChannelCode, request, response);
    }

    private boolean isExternalNoGatewayOrderSource(String orderSource) {
        return DailyTicketOrderSupport.isExternalNoGatewayOrderSource(orderSource);
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        DailyTicketOrderSupport.putIfText(target, key, value);
    }

    private String stringValue(Object value, String defaultValue) {
        return DailyTicketOrderSupport.stringValue(value, defaultValue);
    }

    private boolean isGatewaySuccess(DailyTicketPayGatewayResponse response) {
        return DailyTicketRefundMessages.isGatewaySuccess(response);
    }

    private String validateOrderNo(String orderNo, String orderType) {
        return DailyTicketOrderSupport.validateOrderNo(orderNo, orderType);
    }

    private <T extends DailyTicketBaseResult> T success(T result) {
        return DailyTicketOrderSupport.success(result);
    }

    private <T extends DailyTicketBaseResult> T fail(T result, String message) {
        return DailyTicketOrderSupport.fail(result, message);
    }
}
