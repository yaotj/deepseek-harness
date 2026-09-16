package com.chinasofti.huateng.ticket.notify;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 出向 form-data 报文骨架工厂 —— {@code notify} 包内两条外发链路共用的 8 个公共字段。
 *
 * <p>2026-09-14 从 {@code AppNotifyServiceImpl}（425 行）拆出（ADR-D61）。抽出来的理由不是复用行数，
 * 而是**这 8 个字段是对外契约的一部分**：行业数据推送与支付宝行程推送发的是同一套骨架
 * （{@code providerId} / {@code charset} / {@code format} / {@code timestamp} / {@code deviceId} /
 * {@code signType} / {@code sign} / {@code bizData}），两条链路都已联调通过。留在一个 425 行的类里时，
 * 「改这个方法会同时改动两个已联调的外部契约」这件事在代码里看不出来。
 *
 * <p><b>字段名、顺序、空值兜底 NEVER 改</b>：`deviceId` 为 null 时补空串（支付宝链路恒传空串），
 * 少一个 part 或改一个 key 都会让对方按缺字段拒收，而两条链路都没有幂等键、失败即丢。
 *
 * <p><b>本类不承载签名语义</b>（AGENTS.md §5.1）：`sign` / `signType` 只是从配置透传的占位值
 * （当前 `signType=00` 免签、`sign` 为空），**NEVER 在这里加签**——四条链路的加签验签互不相同。
 */
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
     * @param bizData  业务参数 JSON 串
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
