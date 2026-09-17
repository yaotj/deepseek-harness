package com.chinasofti.huateng.facepay.channel.app;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** APP 网关通知客户端（出向）。 */
@Component
public class AppNotifyClient {

    /** APP 侧成功码，与设备侧 {@code retCode} 同族。 */
    private static final String RET_CODE_SUCCESS = "0000";

    /** 部分网关用 {@code code=0} 表示成功（支付中心那套口径）。 */
    private static final String CODE_SUCCESS = "0";

    /** 信封 {@code timestamp} 格式，与设备链路一致。 */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final Logger log = LoggerFactory.getLogger(AppNotifyClient.class);

    private final AppNotifyProperties properties;

    private final HttpClient httpClient;

    public AppNotifyClient(AppNotifyProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    /**
     * 投递一条通知。
     *
     * @param url         完整 URL；为空时直接返回失败（配置缺失也要留痕，NEVER 静默跳过）
     * @param payloadJson 业务报文 JSON，作为信封的 {@code bizData} 字段值发送
     */
    public AppNotifyResult post(String url, String payloadJson) {
        if (url == null || url.isBlank()) {
            return AppNotifyResult.failed("通知地址未配置，检查 f2f.notify.app.*-url");
        }
        String bizData = payloadJson == null ? "{}" : payloadJson;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody(bizData), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return evaluate(url, response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("通知投递被中断, url={}", url, e);
            return AppNotifyResult.failed("投递被中断");
        } catch (Exception e) {
            log.error("通知投递异常, url={}", url, e);
            return AppNotifyResult.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** 拼 ITP 信封表单体，{@code bizData} 放业务 JSON。 */
    private String formBody(String bizData) {
        StringBuilder body = new StringBuilder();
        append(body, "providerId", properties.getProviderId());
        append(body, "charset", properties.getCharset());
        append(body, "format", properties.getFormat());
        append(body, "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        append(body, "deviceId", properties.getDeviceId());
        append(body, "signType", properties.getSignType());
        append(body, "sign", properties.getSign());
        append(body, "bizData", bizData);
        return body.toString();
    }

    private static void append(StringBuilder body, String name, String value) {
        if (!body.isEmpty()) {
            body.append('&');
        }
        body.append(name).append('=')
                .append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
    }

    /** 判定应答，口径见类注释。 */
    private AppNotifyResult evaluate(String url, int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            return AppNotifyResult.failed("HTTP " + statusCode + ", body=" + abbreviate(body));
        }
        JSONObject json = parse(body);
        String retCode = json == null ? null : json.getString("retCode");
        String code = json == null ? null : json.getString("code");
        if (retCode == null && code == null) {
            log.warn("APP 通知应答无法判定成功码，按 HTTP {} 记为已投递（联调时 MUST 核对应答体）,"
                    + " url={}, body={}", statusCode, url, abbreviate(body));
            return AppNotifyResult.ok();
        }
        if (RET_CODE_SUCCESS.equals(retCode) || CODE_SUCCESS.equals(code)) {
            return AppNotifyResult.ok();
        }
        return AppNotifyResult.failed("对端未受理, retCode=" + retCode + ", code=" + code);
    }

    private static JSONObject parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(body);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return null;
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }

    public AppNotifyProperties properties() {
        return properties;
    }
}
