package com.chinasofti.huateng.ticket.notify;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class FormDataNotifyTemplate {

    private static final Logger log = LoggerFactory.getLogger(FormDataNotifyTemplate.class);

    private final OkHttpClient httpClient;
    private final NotifyFormRequestFactory formRequestFactory;

    protected FormDataNotifyTemplate(OkHttpClient httpClient, NotifyFormRequestFactory formRequestFactory) {
        this.httpClient = httpClient;
        this.formRequestFactory = formRequestFactory;
    }


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


    protected abstract boolean isAccepted(boolean httpSuccessful, String responseBody);
}
