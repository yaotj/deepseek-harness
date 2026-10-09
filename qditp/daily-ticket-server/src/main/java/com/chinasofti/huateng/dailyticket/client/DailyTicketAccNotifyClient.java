package com.chinasofti.huateng.dailyticket.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.dailyticket.config.DailyTicketAccNotifyProperties;
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

/** 日票激活后通知 ACC 发售的 HTTP 客户端。 */
@Component
public class DailyTicketAccNotifyClient {

    private static final Logger log = LoggerFactory.getLogger(DailyTicketAccNotifyClient.class);
    private static final String RET_CODE_SUCCESS = "0000";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int BODY_LOG_LIMIT = 200;

    private final DailyTicketAccNotifyProperties properties;
    private final HttpClient httpClient;

    public DailyTicketAccNotifyClient(DailyTicketAccNotifyProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    public DailyTicketAccNotifyResult post(String bizDataJson) {
        if (properties.getUrl() == null || properties.getUrl().isBlank()) {
            return DailyTicketAccNotifyResult.failed("ACC发售通知地址未配置");
        }
        try {
            String bizData = bizDataJson == null ? "{}" : bizDataJson;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getUrl()))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody(bizData), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return evaluate(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("日票ACC发售通知被中断, url={}", properties.getUrl(), e);
            return DailyTicketAccNotifyResult.failed("投递被中断");
        } catch (Exception e) {
            log.error("日票ACC发售通知异常, url={}", properties.getUrl(), e);
            return DailyTicketAccNotifyResult.failed(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /**
     * 按 ACC/ITP 公共协议组装表单报文，bizData 保持为 JSON 字符串。
     */
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

    private DailyTicketAccNotifyResult evaluate(int statusCode, String body) {
        if (statusCode < 200 || statusCode >= 300) {
            return DailyTicketAccNotifyResult.failed("HTTP " + statusCode + ", body=" + abbreviate(body));
        }
        JSONObject root = parse(body);
        if (root == null) {
            return DailyTicketAccNotifyResult.failed("ACC返回非JSON, body=" + abbreviate(body));
        }
        JSONObject bizData = root.getJSONObject("bizData");
        String retCode = root.getString("retCode");
        if (retCode == null && bizData != null) {
            retCode = bizData.getString("retCode");
        }
        String retMsg = root.getString("retMsg");
        if (retMsg == null && bizData != null) {
            retMsg = bizData.getString("retMsg");
        }
        if (RET_CODE_SUCCESS.equals(retCode)) {
            return DailyTicketAccNotifyResult.ok();
        }
        return DailyTicketAccNotifyResult.failed("ACC未受理, retCode=" + retCode + ", retMsg=" + retMsg);
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
