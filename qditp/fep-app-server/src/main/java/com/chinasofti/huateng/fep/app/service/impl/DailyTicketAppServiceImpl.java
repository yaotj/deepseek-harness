package com.chinasofti.huateng.fep.app.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.fep.app.service.DailyTicketAppService;
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
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.TravelTicketOrderResult;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;

/**
 * APP日票业务前置服务实现。
 *
 * <p>fep-app-server只承接APP入口和服务编排，日票订单、支付、激活、退款等状态由daily-ticket-server维护。</p>
 */
@Service
public class DailyTicketAppServiceImpl implements DailyTicketAppService {
    private static final Logger log = LoggerFactory.getLogger(DailyTicketAppServiceImpl.class);

    private final DailyTicketClient dailyTicketClient;

    public DailyTicketAppServiceImpl(DailyTicketClient dailyTicketClient) {
        this.dailyTicketClient = dailyTicketClient;
    }

    @Override
    public DailyTicketOrderResult requestCountingOrder(DailyTicketOrderReqDTO request) {
        log.info("call daily-ticket requestCountingOrder request={}", JSON.toJSONString(request));
        DailyTicketOrderResult result = dailyTicketClient.requestCountingOrder(request);
        log.info("call daily-ticket requestCountingOrder response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public TravelTicketOrderResult requestTravelOrder(TravelTicketOrderReqDTO request) {
        log.info("call daily-ticket requestTravelOrder request={}", JSON.toJSONString(request));
        TravelTicketOrderResult result = dailyTicketClient.requestTravelOrder(request);
        log.info("call daily-ticket requestTravelOrder response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketPayResult requestPay(DailyTicketPayReqDTO request) {
        log.info("call daily-ticket requestPay request={}", JSON.toJSONString(request));
        DailyTicketPayResult result = dailyTicketClient.requestPay(request);
        log.info("call daily-ticket requestPay response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketPayQueryResult requestPayResult(DailyTicketOrderNoReqDTO request) {
        log.info("call daily-ticket requestPayResult request={}", JSON.toJSONString(request));
        DailyTicketPayQueryResult result = dailyTicketClient.requestPayResult(request);
        log.info("call daily-ticket requestPayResult response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketRefundResult requestRefundTicket(DailyTicketOrderNoReqDTO request) {
        log.info("call daily-ticket requestRefundTicket request={}", JSON.toJSONString(request));
        DailyTicketRefundResult result = dailyTicketClient.requestRefundTicket(request);
        log.info("call daily-ticket requestRefundTicket response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketBaseResult cancelOrder(DailyTicketOrderNoReqDTO request) {
        log.info("call daily-ticket cancelOrder request={}", JSON.toJSONString(request));
        DailyTicketBaseResult result = dailyTicketClient.cancelOrder(request);
        log.info("call daily-ticket cancelOrder response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketBaseResult updateTicket(DailyTicketActivateReqDTO request) {
        log.info("call daily-ticket updateTicket request={}", JSON.toJSONString(request));
        DailyTicketBaseResult result = dailyTicketClient.updateTicket(request);
        log.info("call daily-ticket updateTicket response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketBaseResult updateAndNotice(DailyTicketUsedNoticeReqDTO request) {
        log.info("call daily-ticket updateAndNotice request={}", JSON.toJSONString(request));
        DailyTicketBaseResult result = dailyTicketClient.updateAndNotice(request);
        log.info("call daily-ticket updateAndNotice response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketBaseResult receivePayResult(DailyTicketPayCallbackReqDTO request) {
        log.info("call daily-ticket receivePayResult request={}", JSON.toJSONString(request));
        DailyTicketBaseResult result = dailyTicketClient.receivePayResult(request);
        log.info("call daily-ticket receivePayResult response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public DailyTicketBaseResult handlePayResultCallback(String requestBody) {
        DailyTicketPayCallbackReqDTO callbackRequest = parsePayResultCallback(requestBody);
        if (callbackRequest == null) {
            return failCallback("无效的支付回调参数");
        }
        log.info("日票支付回调解析结果={}", JSON.toJSONString(callbackRequest));
        return receivePayResult(callbackRequest);
    }

    private DailyTicketPayCallbackReqDTO parsePayResultCallback(String requestBody) {
        try {
            JSONObject root = JSON.parseObject(requestBody);
            Object bizData = root.get("bizData");
            JSONObject callbackData;
            if (bizData instanceof JSONObject) {
                callbackData = (JSONObject) bizData;
            } else if (bizData instanceof String && !((String) bizData).trim().isEmpty()) {
                callbackData = JSON.parseObject(decodeBizData((String) bizData));
            } else {
                callbackData = root;
            }

            String orderNo = firstText(callbackData.getString("merchantOrderNo"), callbackData.getString("orderNo"));
            if (orderNo == null) {
                return null;
            }

            DailyTicketPayCallbackReqDTO request = new DailyTicketPayCallbackReqDTO();
            request.setOrderNo(orderNo);
            request.setTradeNo(firstText(callbackData.getString("orderNo"), callbackData.getString("channelOrderNo")));
            request.setPaymentOrderNo(callbackData.getString("orderNo"));
            request.setPayResult(callbackData.getString("status"));
            request.setPayAmount(firstInteger(callbackData.getInteger("cashAmount"), callbackData.getInteger("totalAmount")));
            request.setPayDate(parsePayDate(callbackData.getString("payTime")));
            request.setPayChannel(callbackData.getString("paymentVendor"));
            request.setRawBody(requestBody);
            return request;
        } catch (Exception exception) {
            log.error("解析日票支付回调失败, requestBody={}", requestBody, exception);
            return null;
        }
    }

    private String decodeBizData(String bizData) {
        try {
            return new String(Base64.getDecoder().decode(bizData), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return bizData;
        }
    }

    private Date parsePayDate(String payTime) {
        if (payTime == null || payTime.trim().isEmpty()) {
            return null;
        }
        for (String pattern : new String[]{"yyyyMMddHHmmss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss"}) {
            try {
                SimpleDateFormat formatter = new SimpleDateFormat(pattern);
                formatter.setLenient(false);
                return formatter.parse(payTime);
            } catch (ParseException ignored) {
            }
        }
        log.warn("日票支付回调支付时间无法解析, payTime={}", payTime);
        return null;
    }

    private String firstText(String primary, String fallback) {
        return primary != null && !primary.trim().isEmpty() ? primary : fallback;
    }

    private Integer firstInteger(Integer primary, Integer fallback) {
        return primary != null ? primary : fallback;
    }

    private DailyTicketBaseResult failCallback(String message) {
        DailyTicketBaseResult result = new DailyTicketBaseResult();
        result.setRetCode("9999");
        result.setRetMsg(message);
        return result;
    }
}
