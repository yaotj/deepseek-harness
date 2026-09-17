package com.chinasofti.huateng.rpc.collectpay;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.model.collectpay.AppPayOrderCloseReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderQueryReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderRegisterReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderResultRespDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

/**
 * collect-pay-server RPC client.
 */
@Service
public class CollectPayClient extends ProxyWebClient {

    public CollectPayClient(@Value("${service.collectPay.url:collect-pay-service}") String baseUrl,
                            @Value("${service.collectPay.openLogger:true}") boolean openLogger,
                            WebClient.Builder webClientBuilder) {
        super(baseUrl, openLogger, webClientBuilder);
    }

    /**
     * Forwards the APP order form unchanged to collect-pay-server.
     */
    public JSONObject requestOrder(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestOrder", formData);
        return JSON.parseObject(result);
    }

    /**
     * Forwards the APP payment-info form unchanged to collect-pay-server.
     */
    public JSONObject requestPaymentInfo(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestPaymentInfo", formData);
        return JSON.parseObject(result);
    }

    /**
     * Forwards the APP payment-result query form unchanged to collect-pay-server.
     */
    public JSONObject requestPayResult(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestPayResult", formData);
        return JSON.parseObject(result);
    }

    /**
     * Forwards the APP request-pay-order form unchanged to collect-pay-server.
     */
    public JSONObject requestPayOrder(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestPayOrder", formData);
        return JSON.parseObject(result);
    }

    public JSONObject requestRefundTicket(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestRefundTicket", formData);
        return JSON.parseObject(result);
    }

    public JSONObject requestRefundTicketResult(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestRefundTicketResult", formData);
        return JSON.parseObject(result);
    }

    public JSONObject requestPreActiveOrderList(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/requestPreActiveOrderList", formData);
        return JSON.parseObject(result);
    }

    public JSONObject requestActiveTicket(Map<String, String> formData) {
        String result = postFormAndGetResponse("/itptvm/ci/tvm/requestActiveTicket", formData);
        return JSON.parseObject(result);
    }

    public JSONObject receiveRefundResult(Map<String, String> formData) {
        String result = postFormAndGetResponse("/ci/app/receiveRefundResult", formData);
        return JSON.parseObject(result);
    }

    /**
     * 把一张外部单据登记成 APP 订单行 + 支付前置单行，让乘客能走 collect-pay 的收银台把它付掉。
     */
    public RpcOutcome registerAppPayOrder(AppPayOrderRegisterReqDTO request) {
        String response;
        try {
            response = postJsonAndGetResponse("/internal/app-order/register", request);
        } catch (Exception e) {
            return new RpcOutcome.Unreachable(e);
        }
        return parseOutcome(response);
    }

    /**
     * 按订单号把 APP 订单表上仍待支付的行置为支付失败（对端带 {@code PAY_STATUS='0'} 白名单）。
     */
    public RpcOutcome closeUnpaidAppPayOrder(AppPayOrderCloseReqDTO request) {
        String response;
        try {
            response = postJsonAndGetResponse("/internal/app-order/close-unpaid", request);
        } catch (Exception e) {
            return new RpcOutcome.Unreachable(e);
        }
        return parseOutcome(response);
    }

    /**
     * 按订单号回查 APP 订单表的支付结果。
     */
    public AppPayOrderResultRespDTO queryAppPayOrderResult(AppPayOrderQueryReqDTO request) {
        String response = postJsonAndGetResponse("/internal/app-order/pay-result", request);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("collect-pay 回查 APP 订单支付结果返回空响应, orderNo="
                    + (request == null ? null : request.getOrderNo()));
        }
        return JSON.parseObject(response, AppPayOrderResultRespDTO.class);
    }

    /**
     * 把对端应答的 {@code retCode} 归成 {@code RpcOutcome}。
     */
    private RpcOutcome parseOutcome(String response) {
        if (response == null || response.isBlank()) {
            return new RpcOutcome.BizRejected(null, "collect-pay 返回空响应");
        }
        JSONObject body = JSON.parseObject(response);
        if (body == null) {
            return new RpcOutcome.BizRejected(null, "collect-pay 返回无法解析的响应");
        }
        return RpcOutcome.ofRetCode(body.getString("retCode"), body.getString("retMsg"));
    }

}
