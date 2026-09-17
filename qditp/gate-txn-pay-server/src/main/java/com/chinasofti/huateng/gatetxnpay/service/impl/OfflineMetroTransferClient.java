package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/** 离线码钱包订单的公交换乘资格查询。 */
@Service
public class OfflineMetroTransferClient extends ProxyWebClient {
    private static final Logger log = LoggerFactory.getLogger(OfflineMetroTransferClient.class);
    private final String url;

    public OfflineMetroTransferClient(
            @Value("${wallet.metro-transfer-check-url:http://172.20.202.10:8885/buscard/busApi/2App/v1/checkMetroTransfer}") String url,
            @Value("${wallet.metro-transfer-check-open-logger:false}") boolean openLogger,
            @Value("${wallet.metro-transfer-check-timeout-ms:3000}") long timeoutMs,
            WebClient.Builder webClientBuilder) {
        super(url, openLogger, webClientBuilder, Duration.ofMillis(timeoutMs));
        this.url = url;
    }

    public boolean isReduction(String thirdUserId, String handleDateTime, String cardId, String ticketTransSeq) {
        try {
            Map<String, Object> bizData = new LinkedHashMap<>();
            bizData.put("thirdUserId", thirdUserId);
            bizData.put("handleDateTime", handleDateTime);
            bizData.put("cardId", cardId);
            bizData.put("ticketTransSeq", ticketTransSeq);
            Map<String, String> request = new LinkedHashMap<>();
            request.put("providerId", "01");
            request.put("charset", "utf-8");
            request.put("format", "json");
            request.put("timestamp", String.valueOf(System.currentTimeMillis()));
            request.put("signType", "00");
            request.put("bizData", JSON.toJSONString(bizData));
            String response = postFormAndGetResponse(url, request, null);
            if (response == null) {
                throw new IllegalStateException("公交换乘查询无响应");
            }
            var responseJson = JSON.parseObject(response);
            String retCode = responseJson.getString("retCode");
            if (!GateTxnPayRetCode.SUCCESS.equals(retCode)) {
                throw new IllegalStateException("公交换乘查询失败：" + response);
            }
            String reduction = responseJson.getString("isReduction");
            log.info("离线码公交换乘查询完成, cardId={}, ticketTransSeq={}, isReduction={}", cardId, ticketTransSeq, reduction);
            return "02".equals(reduction);
        } catch (RuntimeException e) {
            if (e instanceof IllegalStateException) {
                throw e;
            }
            throw new IllegalStateException("公交换乘查询异常", e);
        }
    }
}
