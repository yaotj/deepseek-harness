package com.chinasofti.huateng.facepay.channel.paycenter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** 支付中心 HTTP 通道。NEVER 在 {@code @Transactional} 方法里调本类。 */
@Component
public class PayCenterClient {

    private static final Logger log = LoggerFactory.getLogger(PayCenterClient.class);

    private final PayCenterProperties properties;

    private final HttpClient httpClient;

    private final Duration readTimeout;

    public PayCenterClient(PayCenterProperties properties) {
        this.properties = properties;
        this.readTimeout = Duration.ofMillis(properties.getReadTimeoutMs());
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    /**
     * 发一次支付中心请求。
     *
     * @param url     完整 URL，取自 {@code pay.center.*-url}，NEVER 由 base+path 拼
     * @param request 已签名的信封
     */
    public PayCenterResult execute(String url, PayCenterRequest request) {
        if (url == null || url.isBlank()) {
            log.error("支付中心接口地址未配置, request={}", request);
            return PayCenterResult.unknown("支付中心接口地址未配置", null, 0L);
        }
        String body = JSON.toJSONString(request);
        long start = System.nanoTime();
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("Accept", "*/*")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> httpResponse =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long costMs = costMs(start);
            String raw = httpResponse.body();
            if (httpResponse.statusCode() / 100 != 2) {
                log.error("支付中心响应非2xx, url={}, httpStatus={}, costMs={}", url, httpResponse.statusCode(), costMs);
                return PayCenterResult.unknown("httpStatus=" + httpResponse.statusCode(), raw, costMs);
            }
            return parse(url, raw, costMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long costMs = costMs(start);
            log.error("支付中心调用被中断, url={}, costMs={}", url, costMs);
            return PayCenterResult.unknown("interrupted", null, costMs);
        } catch (Exception e) {
            long costMs = costMs(start);
            log.error("支付中心调用异常, url={}, costMs={}, request={}", url, costMs, request, e);
            return PayCenterResult.unknown(e.getClass().getSimpleName() + ": " + e.getMessage(), null, costMs);
        }
    }

    private PayCenterResult parse(String url, String raw, long costMs) {
        if (raw == null || raw.isBlank()) {
            log.error("支付中心响应体为空, url={}, costMs={}", url, costMs);
            return PayCenterResult.unknown("响应体为空", raw, costMs);
        }
        JSONObject json;
        try {
            json = JSON.parseObject(raw);
        } catch (Exception e) {
            log.error("支付中心响应体不是合法JSON, url={}, costMs={}", url, costMs, e);
            return PayCenterResult.unknown("响应体非JSON", raw, costMs);
        }
        if (json == null) {
            return PayCenterResult.unknown("响应体解析为null", raw, costMs);
        }
        Object code = json.get("code");
        String msg = json.getString("msg");
        Map<String, Object> data = json.getJSONObject("data");
        log.info("支付中心响应, url={}, code={}, msg={}, status={}, costMs={}",
                url, code, msg, data == null ? null : data.get("status"), costMs);
        return PayCenterResult.answered(code == null ? null : String.valueOf(code), msg, data, raw, costMs);
    }

    private static long costMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /** 供上层按业务场景取 URL，避免各处再 autowire 一份配置。 */
    public PayCenterProperties properties() {
        return properties;
    }
}
