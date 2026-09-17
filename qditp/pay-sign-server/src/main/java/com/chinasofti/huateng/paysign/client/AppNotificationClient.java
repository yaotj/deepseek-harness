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

/** APP 回调 HTTP 客户端。 */
@Component
public class AppNotificationClient {
    private static final Logger log = LoggerFactory.getLogger(AppNotificationClient.class);

    /** 视为通知成功的业务码，逗号分隔。默认只有 {@code 0000}。 */
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

    /** 发送 ITP 标准表单。仅当 HTTP 2xx 且业务回执码命中 {@link #successRetCodes} 时视为成功。 */
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
