package com.chinasofti.huateng.micro.web.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public interface InternalMicroHttp<A> extends MicroHttp {

    Logger log = LoggerFactory.getLogger(InternalMicroHttp.class);

    boolean isOpenLogger();

    void setOpenLogger(boolean open);

    A getProxyHttpClient();

    void setProxyHttpClient(A proxyHttpClient);

    void setBaseUrl(String baseUrl);

    String getBaseUrl();

    default void logRequest(String url, Object requestBody, Map<String, String> headers, String httpMethod) {
        if (isOpenLogger()) {
            log.info("proxy http request method={},url={}/{},requestBody={},headers={}",
                    httpMethod, getBaseUrl(), url, requestBody, headers);
        }
    }

    default void logResponse(int httpStatus, String msg) {
        if (isOpenLogger()) {
            log.info("proxy http response status={},body={}", httpStatus, msg);
        }
    }

    default Map<String, String> createJsonHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", "application/json");
        headers.put("accept", "application/json");
        return headers;
    }
}
