package com.chinasofti.huateng.rpc.collectpay;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
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

}
