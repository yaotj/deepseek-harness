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

@Component
public class DailyTicketAppNotifyClient {

    private static final String RET_CODE_SUCCESS = "0000";

    private static final String CODE_SUCCESS = "0";

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
