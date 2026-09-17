package com.chinasofti.huateng.ticket.notify;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 出向 form-data 报文骨架工厂 —— {@code notify} 包内两条外发链路共用的 8 个公共字段。 */
@Component
class NotifyFormRequestFactory {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Value("${app.notify.provider-id:01}")
    private String providerId;

    @Value("${app.notify.charset:UTF-8}")
    private String charset;

    @Value("${app.notify.format:json}")
    private String format;

    @Value("${app.notify.sign-type:00}")
    private String signType;

    @Value("${app.notify.sign:}")
    private String sign;

    /**
     * 组装 form-data 请求体。
     *
     * @param bizData 业务参数 JSON 串
     * @param deviceId 设备号，可为 null（支付宝行程链路恒传空串）
     */
    RequestBody buildFormDataRequestBody(String bizData, String deviceId) {
        return new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("providerId", providerId)
                .addFormDataPart("charset", charset)
                .addFormDataPart("format", format)
                .addFormDataPart("timestamp", LocalDateTime.now().format(TIMESTAMP_FORMATTER))
                .addFormDataPart("deviceId", deviceId == null ? "" : deviceId)
                .addFormDataPart("signType", signType)
                .addFormDataPart("sign", sign)
                .addFormDataPart("bizData", bizData)
                .build();
    }
}
