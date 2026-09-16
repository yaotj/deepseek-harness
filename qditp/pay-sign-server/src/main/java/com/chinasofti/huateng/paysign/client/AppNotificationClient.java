package com.chinasofti.huateng.paysign.client;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * APP 回调 HTTP 客户端。
 *
 * <p>该类只处理 ITP 公共字段的表单发送和回执判定，不参与签约、解约状态的业务决策，
 * 因而签约与解约通知可以复用同一套可靠的传输行为。</p>
 */
@Component
public class AppNotificationClient {
    private static final Logger log = LoggerFactory.getLogger(AppNotificationClient.class);

    /**
     * 视为通知成功的业务码，逗号分隔。默认只有 {@code 0000}。
     *
     * <p>APP 侧若存在幂等码（重复通知时返回「已处理」而非 0000），把该码加进来即可，
     * 无需改代码重新打包。**NEVER 凭猜测加码**：加错会把真实失败判成成功，
     * 从此不再重试、补偿也扫不到、`NOTIFY_RESULT` 却记着成功，事后无从发现；
     * 而判成失败最多是重试 3 次后停在 FAILED，数据可见、可人工重放。
     * 加码前 MUST 拿到 APP 侧码表或书面确认。</p>
     */
    private final Set<String> successRetCodes;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    public AppNotificationClient(@Value("${app.notify.success-ret-codes:0000}") String successRetCodeConfig) {
        this.successRetCodes = Arrays.stream(successRetCodeConfig.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toUnmodifiableSet());
        log.info("APP 通知成功码集合={}", successRetCodes);
    }

    /**
     * 发送 ITP 标准表单。仅当 HTTP 2xx 且业务回执码命中 {@link #successRetCodes} 时视为成功。
     *
     * <p>空响应体按**失败**处理：APP 侧正常应答一定带 `retCode`，空体只可能是对端异常
     * （网关截断、502 被中间层改写成 200、应用抛异常后返回空）。把空体当成功会让通知
     * 静默丢失且不再重试，宁可多发一次由 APP 幂等吸收。</p>
     */
    public NotificationResult notify(String url, NotificationRequest request) {
        try {
            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("providerId", valueOrEmpty(request.providerId()))
                    .addFormDataPart("charset", valueOrEmpty(request.charset()))
                    .addFormDataPart("format", valueOrEmpty(request.format()))
                    .addFormDataPart("timestamp", valueOrEmpty(request.timestamp()))
                    .addFormDataPart("deviceId", valueOrEmpty(request.deviceId()))
                    .addFormDataPart("signType", valueOrEmpty(request.signType()))
                    .addFormDataPart("sign", valueOrEmpty(request.sign()))
                    .addFormDataPart("bizData", request.bizDataJson())
                    .build();
            Request httpRequest = new Request.Builder().url(url).post(requestBody).build();

            try (Response response = httpClient.newCall(httpRequest).execute()) {
                String body = response.body() == null ? null : response.body().string();
                if (!response.isSuccessful()) {
                    return NotificationResult.failure("HTTP" + response.code());
                }
                if (!StringUtils.hasText(body)) {
                    log.warn("APP 通知响应体为空, 按失败处理, url={}, httpCode={}", url, response.code());
                    return NotificationResult.failure("响应体为空");
                }
                PaySignCallbackResult result = JSON.parseObject(body, PaySignCallbackResult.class);
                if (result == null || !StringUtils.hasText(result.getRetCode())) {
                    log.warn("APP 通知响应无 retCode, 按失败处理, url={}, body={}", url, body);
                    return NotificationResult.failure("响应无retCode");
                }
                return successRetCodes.contains(result.getRetCode())
                        ? NotificationResult.succeeded()
                        : NotificationResult.failure("业务失败:" + result.getRetCode() + ":" + result.getRetMsg());
            }
        } catch (IOException e) {
            log.warn("APP 通知网络异常, url={}", url, e);
            return NotificationResult.failure("网络异常:" + e.getMessage());
        } catch (Exception e) {
            log.warn("APP 通知处理异常, url={}", url, e);
            return NotificationResult.failure("异常:" + e.getMessage());
        }
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    /** ITP 表单请求的传输模型，避免业务服务直接依赖 OkHttp。 */
    public record NotificationRequest(String providerId, String charset, String format, String timestamp,
                                      String deviceId, String signType, String sign, String bizDataJson) {
    }

    /** 通知处理结果，供调用方统一持久化通知状态与失败原因。 */
    public record NotificationResult(boolean success, String message) {
        public static NotificationResult succeeded() {
            return new NotificationResult(true, "通知成功");
        }

        public static NotificationResult failure(String message) {
            return new NotificationResult(false, message);
        }
    }
}
