package com.chinasofti.huateng.dailyticket.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.dailyticket.config.DailyTicketAppNotifyProperties;
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
import java.util.UUID;

/** APP 网关通知客户端（出向）。 */
@Component
public class DailyTicketAppNotifyClient {

    /** APP 侧成功码。 */
    private static final String RET_CODE_SUCCESS = "0000";

    /** 部分网关用 {@code code=0} 表示成功（支付中心那套口径）。 */
    private static final String CODE_SUCCESS = "0";

    /** 信封 {@code timestamp} 格式。 */
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final int BODY_LOG_LIMIT = 200;

    private static final Logger log = LoggerFactory.getLogger(DailyTicketAppNotifyClient.class);

    private final DailyTicketAppNotifyProperties properties;

    private final HttpClient httpClient;

    public DailyTicketAppNotifyClient(DailyTicketAppNotifyProperties properties) {
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
    public DailyTicketAppNotifyResult post(String url, String payloadJson) {
        if (url == null || url.isBlank()) {
            return DailyTicketAppNotifyResult.failed("通知地址未配置");
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
            log.error("日票APP通知投递被中断, url={}", url, e);
            return DailyTicketAppNotifyResult.failed("投递被中断");
        } catch (Exception e) {
            log.error("日票APP通知投递异常, url={}", url, e);
            return DailyTicketAppNotifyResult.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 按 APP 接口协议投递 multipart/form-data 通知。
     *
     * <p>IF8B-05 支付结果通知要求外层为 ITP 表单信封，{@code bizData} 是原始 JSON 字符串：
     * 不做 Base64，不把业务字段平铺到外层表单。</p>
     */
    public DailyTicketAppNotifyResult postMultipart(String url, String payloadJson) {
        if (url == null || url.isBlank()) {
            return DailyTicketAppNotifyResult.failed("通知地址未配置");
        }
        String bizData = payloadJson == null ? "{}" : payloadJson;
        String boundary = "----DailyTicketAppNotify" + UUID.randomUUID().toString().replace("-", "");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofString(multipartBody(boundary, bizData), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return evaluate(url, response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("日票APP multipart通知投递被中断, url={}", url, e);
            return DailyTicketAppNotifyResult.failed("投递被中断");
        } catch (Exception e) {
            log.error("日票APP multipart通知投递异常, url={}", url, e);
            return DailyTicketAppNotifyResult.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
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

    private String multipartBody(String boundary, String bizData) {
        StringBuilder body = new StringBuilder();
        appendPart(body, boundary, "providerId", properties.getProviderId());
        appendPart(body, boundary, "charset", properties.getCharset());
        appendPart(body, boundary, "format", properties.getFormat());
        appendPart(body, boundary, "timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER));
        appendPart(body, boundary, "deviceId", properties.getDeviceId());
        appendPart(body, boundary, "signType", properties.getSignType());
        appendPart(body, boundary, "sign", properties.getSign());
        appendPart(body, boundary, "bizData", bizData);
        body.append("--").append(boundary).append("--").append("\r\n");
        return body.toString();
    }

    private static void append(StringBuilder body, String name, String value) {
        if (!body.isEmpty()) {
            body.append('&');
        }
        body.append(name).append('=')
                .append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
    }

    private static void appendPart(StringBuilder body, String boundary, String name, String value) {
        body.append("--").append(boundary).append("\r\n");
        body.append("Content-Disposition: form-data; name=\"").append(name).append("\"\r\n\r\n");
        body.append(value == null ? "" : value).append("\r\n");
    }

    /** 判定应答，口径见类注释。 */
    private DailyTicketAppNotifyResult evaluate(String url, int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            return DailyTicketAppNotifyResult.failed("HTTP " + statusCode + ", body=" + abbreviate(body));
        }
        JSONObject json = parse(body);
        String retCode = json == null ? null : json.getString("retCode");
        String code = json == null ? null : json.getString("code");
        if (retCode == null && code == null) {
            log.warn("日票APP通知应答无法判定成功码，按 HTTP {} 记为已投递（联调时 MUST 核对应答体）, url={}, body={}",
                    statusCode, url, abbreviate(body));
            return DailyTicketAppNotifyResult.ok();
        }
        if (RET_CODE_SUCCESS.equals(retCode) || CODE_SUCCESS.equals(code)) {
            return DailyTicketAppNotifyResult.ok();
        }
        return DailyTicketAppNotifyResult.failed("对端未受理, retCode=" + retCode + ", code=" + code);
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
        return body.length() <= BODY_LOG_LIMIT ? body : body.substring(0, BODY_LOG_LIMIT) + "...";
    }
}
