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

/**
 * 支付中心 HTTP 通道。<b>本类只管发报文、收报文、记耗时，不含任何业务判断。</b>
 *
 * <h2>四条硬性约束</h2>
 * <ol>
 *   <li><b>NEVER 在 {@code @Transactional} 方法里调本类。</b>事务包住网络调用会把行锁持有时长
 *       拉长到对端响应时长，2026-08-26 生产事故即此（AGENTS.md §5.2）。</li>
 *   <li><b>本类不抛异常、也不返回 null。</b>传输层失败返回
 *       {@link PayCenterResult#isTransportFailed()} 为真的结果，调用方按 UNKNOWN 落库。</li>
 *   <li><b>NEVER 打印 {@code bizData} 明文、{@code sign}、私钥。</b>请求侧只打
 *       {@link PayCenterRequest#toString()}（已脱敏）。</li>
 *   <li>响应原文全量返回给调用方落 {@code F2F_PAYMENT.RESPONSE_BODY} 留证，出错时也留。</li>
 * </ol>
 *
 * <p>传输用 JDK 21 内置 {@code java.net.http.HttpClient}：不引新依赖，且它在虚拟线程上阻塞
 * 不会 pin 载体线程（旧实现用的 commons-httpclient 3.x 已不维护）。</p>
 */
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
        // status 原样打出来：支付中心网关文档 §5.1 没有列 status 值域，实测取值只能靠日志反推。
        // 2026-09-11 已因此踩过一次（枚举缺 FAIL，支付失败回调收不了口），MUST 保留这一列。
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
