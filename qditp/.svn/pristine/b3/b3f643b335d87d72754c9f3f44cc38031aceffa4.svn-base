package com.chinasofti.huateng.micro.web.client;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractMicroHttp<A> implements InternalMicroHttp<A> {
    boolean openLogger;

    @Override
    public boolean isOpenLogger() {
        return openLogger;
    }

    @Override
    public void setOpenLogger(boolean openLogger) {
        this.openLogger = openLogger;
    }

    String baseUrl;

    @Override
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    A proxyHttpClient;

    @Override
    public A getProxyHttpClient() {
        return proxyHttpClient;
    }

    @Override
    public void setProxyHttpClient(A proxyHttpClient) {
        this.proxyHttpClient = proxyHttpClient;
    }

    @Override
    public String postJsonAndGetResponse(String url, Object requestBody) {
        return postJsonAndGetResponse(url, requestBody, new HashMap<>());
    }

    @Override
    public String getAndGetResponse(String url, Map<String, String> params) {
        return getAndGetResponse(url, params, new HashMap<>());
    }

    @Override
    public String postFormAndGetResponse(String url, Map<String, String> formData) {
        return postFormAndGetResponse(url, formData, new HashMap<>());
    }

}
