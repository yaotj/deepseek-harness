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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * APP 回调 HTTP 客户端。
 *
 * <p>该类只处理 ITP 公共字段的表单发送和回执判定，不参与签约、解约状态的业务决策，
 * 因而签约与解约通知可以复用同一套可靠的传输行为。</p>
 */
@Component
public class AppNotificationClient {
    private static final Logger log = LoggerFactory.getLogger(AppNotificationClient.class);

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build();

    /**
     * 发送 ITP 标准表单。HTTP 2xx 且业务回执为空或返回 {@code 0000} 时视为成功。
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
                    return NotificationResult.succeeded();
                }
                PaySignCallbackResult result = JSON.parseObject(body, PaySignCallbackResult.class);
                return result == null || "0000".equals(result.getRetCode())
                        ? NotificationResult.succeeded()
                        : NotificationResult.failure("业务失败:" + result.getRetMsg());
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
