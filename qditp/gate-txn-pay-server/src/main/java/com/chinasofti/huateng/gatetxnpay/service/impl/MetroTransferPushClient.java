package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.gatetxnpay.entity.MetroTransferPushTask;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** 公交换乘行程推送适配器。 */
@Service
public class MetroTransferPushClient extends ProxyWebClient {
    private static final Logger log = LoggerFactory.getLogger(MetroTransferPushClient.class);
    private final String url;
    private final boolean enabled;

    public MetroTransferPushClient(
            @Value("${wallet.metro-transfer-url:http://172.20.202.10:8885/buscard/busApi/2App/v1/pushMetroTran}") String url,
            @Value("${wallet.metro-transfer-enabled:true}") boolean enabled,
            @Value("${wallet.metro-transfer-open-logger:false}") boolean openLogger,
            @Value("${wallet.metro-transfer-timeout-ms:3000}") long timeoutMs,
            WebClient.Builder webClientBuilder) {
        super(url, openLogger, webClientBuilder, Duration.ofMillis(timeoutMs));
        this.url = url;
        this.enabled = enabled;
    }

    /** 推送一笔换乘行程，返回三态结果而不是 void + 抛异常（AGENTS.md §5.2 / ADR-D45）。 */
    public RpcOutcome push(MetroTransferPushTask task) {
        if (!enabled) {
            throw new IllegalStateException("公交换乘推送未启用");
        }
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("thirdUserId", task.getThirdUserId());
        bizData.put("transDate", task.getTransDate());
        bizData.put("transTime", task.getTransTime());
        bizData.put("payChannelType", task.getPayChannelType());
        bizData.put("transferFlag", task.getTransferFlag());
        Map<String, String> request = new LinkedHashMap<>();
        request.put("providerId", "01");
        request.put("charset", "utf-8");
        request.put("format", "json");
        request.put("timestamp", String.valueOf(System.currentTimeMillis()));
        request.put("signType", "00");
        request.put("bizData", JSON.toJSONString(bizData));
        String response;
        try {
            response = postFormAndGetResponse(url, request, null);
        } catch (RuntimeException e) {
            return new RpcOutcome.Unreachable(e);
        }
        String retCode;
        try {
            retCode = response == null ? null : JSON.parseObject(response).getString("retCode");
        } catch (RuntimeException e) {
            return new RpcOutcome.BizRejected("PARSE_ERROR", "对端响应无法解析：" + response);
        }
        RpcOutcome outcome = RpcOutcome.ofRetCode(retCode, response);
        if (outcome.isOk()) {
            log.info("公交换乘行程推送完成, orderNo={}, response={}", task.getOrderNo(), response);
        }
        return outcome;
    }
}
