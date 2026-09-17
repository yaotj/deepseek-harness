package com.chinasofti.huateng.ticket.notify;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** form-data 外发推送骨架（Template Method）—— {@code notify} 包内两条外发链路共用的 「组 form-data → POST → 读应答 → 判受理 → 统一日志与兜底」流程。 */
abstract class FormDataNotifyTemplate {

    private static final Logger log = LoggerFactory.getLogger(FormDataNotifyTemplate.class);

    private final OkHttpClient httpClient;
    private final NotifyFormRequestFactory formRequestFactory;

    protected FormDataNotifyTemplate(OkHttpClient httpClient, NotifyFormRequestFactory formRequestFactory) {
        this.httpClient = httpClient;
        this.formRequestFactory = formRequestFactory;
    }

    /**
     * 模板方法：组 form-data → POST → 交 {@link #isAccepted} 判定 → 统一日志。
     *
     * @param targetUrl 目标地址，由子类从各自的配置键取
     * @param bizData 业务参数 JSON 串，原样进 form-data 的 {@code bizData}
     * @param deviceId 设备号，可为 null（支付宝行程链路恒传空串）
     * @param linkName 链路名，只用于日志，便于按链路过滤
     * @return true 仅当 {@link #isAccepted} 判定受理；
     */
    protected final boolean post(String targetUrl, String bizData, String deviceId, String linkName) {
        RequestBody requestBody = formRequestFactory.buildFormDataRequestBody(bizData, deviceId);
        Request httpRequest = new Request.Builder()
                .url(targetUrl)
                .post(requestBody)
                .build();

        log.info("调用{}, url={}, bizData={}", linkName, targetUrl, bizData);
        try (Response response = httpClient.newCall(httpRequest).execute()) {
            String responseBody = response.body() == null ? null : response.body().string();
            boolean accepted = isAccepted(response.isSuccessful(), responseBody);
            if (accepted) {
                log.info("调用{}成功, httpCode={}, response={}", linkName, response.code(), responseBody);
            } else {
                log.warn("调用{}未受理, httpCode={}, url={}, response={}",
                        linkName, response.code(), targetUrl, responseBody);
            }
            return accepted;
        } catch (Exception e) {
            log.error("调用{}异常, url={}, bizData={}", linkName, targetUrl, bizData, e);
            return false;
        }
    }

    /**
     * 应答判定钩子 —— 两条链路口径不同，本类刻意不给默认实现。
     *
     * @param httpSuccessful okhttp 的 {@code Response#isSuccessful}，即 HTTP 2xx
     * @param responseBody 应答体，可能为 null（对方无 body）或非 JSON
     */
    protected abstract boolean isAccepted(boolean httpSuccessful, String responseBody);
}
