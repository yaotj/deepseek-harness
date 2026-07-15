package com.chinasofti.huateng.rpc.dailyticket;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.json.JSONUtil;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * daily-ticket-server RPC客户端。
 */
@Service
public class DailyTicketClient extends ProxyWebClient {
    public DailyTicketClient(@Value("${service.dailyTicket.url:daily-ticket-service}") String baseUrl,
                             @Value("${service.dailyTicket.openLogger:true}") boolean openLogger,
                             WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * 调用日票下单接口。
     */
    public DailyTicketOrderResult requestCountingOrder(@RequestBody DailyTicketOrderReqDTO request) {
        String result = postJsonAndGetResponse("/ci/daily-ticket/requestCountingOrder", request);
        return JSONUtil.toBean(result, new TypeReference<DailyTicketOrderResult>() {
        }, true);
    }

    /**
     * 调用日票支付接口。
     */
    public DailyTicketPayResult requestPay(@RequestBody DailyTicketPayReqDTO request) {
        String result = postJsonAndGetResponse("/ci/daily-ticket/payment/requestPay", request);
        return JSONUtil.toBean(result, new TypeReference<DailyTicketPayResult>() {
        }, true);
    }

    /**
     * 调用日票支付结果查询接口。
     */
    public DailyTicketPayQueryResult requestPayResult(@RequestBody DailyTicketOrderNoReqDTO request) {
        String result = postJsonAndGetResponse("/ci/daily-ticket/payment/requestPayResult", request);
        return JSONUtil.toBean(result, new TypeReference<DailyTicketPayQueryResult>() {
        }, true);
    }

    /**
     * 调用日票退款接口。
     */
    public DailyTicketRefundResult requestRefundTicket(@RequestBody DailyTicketOrderNoReqDTO request) {
        String result = postJsonAndGetResponse("/ci/daily-ticket/payment/requestRefundTicket", request);
        return JSONUtil.toBean(result, new TypeReference<DailyTicketRefundResult>() {
        }, true);
    }

    /**
     * 调用日票取消订单接口。
     */
    public DailyTicketBaseResult cancelOrder(@RequestBody DailyTicketOrderNoReqDTO request) {
        String result = postJsonAndGetResponse("/ci/daily-ticket/ticket/cancelOrder", request);
        return JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
    }

    /**
     * 调用日票激活接口。
     */
    public DailyTicketBaseResult updateTicket(@RequestBody DailyTicketActivateReqDTO request) {
        String result = postJsonAndGetResponse("/ci/daily-ticket/ticket/updateTicket", request);
        return JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
    }

    /**
     * 调用通知ACC车票已使用接口。
     */
    public DailyTicketBaseResult updateAndNotice(@RequestBody DailyTicketUsedNoticeReqDTO request) {
        String result = postJsonAndGetResponse("/ci/daily-ticket/ticket/updateAndNotice", request);
        return JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
    }

    /**
     * 转发支付结果回调到日票服务。
     */
    public DailyTicketBaseResult receivePayResult(@RequestBody DailyTicketPayCallbackReqDTO request) {
        String result = postJsonAndGetResponse("/ci/daily-ticket/payment/receivePayResult", request);
        return JSONUtil.toBean(result, new TypeReference<DailyTicketBaseResult>() {
        }, true);
    }
}
