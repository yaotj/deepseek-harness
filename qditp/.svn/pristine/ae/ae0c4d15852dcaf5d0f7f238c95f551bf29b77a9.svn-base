package com.chinasofti.huateng.micro.web.client;

import cn.hutool.json.JSONUtil;
import okhttp3.*;
import org.slf4j.MDC;
import org.springframework.http.client.MultipartBodyBuilder;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class ProxyOkHttp extends AbstractMicroHttp<OkHttpClient> {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    ConnectionPool connectionPool;

    /**
     * @param baseUrl            value is like http://localhost:8080
     * @param openLogger         print log
     * @param maxIdleConnections http connectionPool of maxIdleConnections
     * @param keepAliveDuration  http connectionPool of keepAliveDuration, TimeUnit is MILLISECONDS
     * @apiNote The simple of use:
     * <pre>{@code
     *     public ClientBOkHttp(@Value("${other.service.b.url}") String baseUrl, @Value("${other.service.b.openLogger}") boolean openLogger, @Value("${other.service.b.maxIdleConnections}") int maxIdleConnections, @Value("${other.service.b.keepAliveDuration}") long keepAliveDuration) {
     *       super(baseUrl, openLogger, maxIdleConnections, keepAliveDuration);
     *     }
     * }</pre>
     */
    public ProxyOkHttp(String baseUrl, boolean openLogger, int maxIdleConnections, long keepAliveDuration) {
        this.baseUrl = baseUrl;
        setOpenLogger(openLogger);
        boolean secure = baseUrl.startsWith("https") ? true : false;
        connectionPool = new ConnectionPool(maxIdleConnections, keepAliveDuration, TimeUnit.SECONDS);
        proxyHttpClient = baseBuilder(secure).connectionPool(connectionPool).build();
    }

    /**
     * @param baseUrl    value is like http://localhost:8080
     * @param openLogger print log
     * @apiNote The simple of use:
     * <pre>{@code
     *     public ClientBOkHttp(@Value("${other.service.b.url}") String baseUrl, @Value("${other.service.b.openLogger}") boolean openLogger) {
     *       super(baseUrl, openLogger);
     *     }
     * }</pre>
     */
    public ProxyOkHttp(String baseUrl, boolean openLogger) {
        this.baseUrl = baseUrl;
        setOpenLogger(openLogger);
        boolean secure = baseUrl.startsWith("https") ? true : false;
        proxyHttpClient = baseBuilder(secure).build();
    }

    private OkHttpClient.Builder baseBuilder(boolean secure) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder().connectTimeout(2000, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (secure) {
            try {
                TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {

                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {

                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }};
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new SecureRandom());
                HostnameVerifier allHostsValid = (hostname, session) -> true;

                builder.sslSocketFactory(sslContext.getSocketFactory(), (X509TrustManager) trustAllCerts[0]).hostnameVerifier(allHostsValid);
            } catch (Exception e) {
                log.error("{}", e.getMessage(), e);
            }
        }
        return builder;
    }

    private void commonHeaders(Request.Builder requestBuilder, Map<String, String> headers) {
        try {
            String authorization = MDC.get("authorization");
            if (authorization != null && !authorization.isEmpty())
                requestBuilder.addHeader("authorization", authorization);
        } catch (Exception e) {
            log.error("{}", e.getMessage(), e);
        }

        if (headers == null) {
            headers = new HashMap<>();
        }
        headers.put("b3", MDC.get("traceId") + "-" + MDC.get("spanId") + "-1");

        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                requestBuilder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        if (connectionPool != null) {
            log.info("connectionPool connectionCount={} idleCount={}", connectionPool.connectionCount(), connectionPool.idleConnectionCount());
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T send(Request request, Class<T> responseClass) {
        try (Response response = getProxyHttpClient().newCall(request).execute()) {
            int statusCode = response.code();
            String json = "";
            ResponseBody responseBody = response.body();
            if (responseBody != null) {
                json = responseBody.string();
                responseBody.close();
            }
            logResponse(statusCode, json);
            String contentType = response.headers().get("Content-Type");
            if (contentType != null && contentType.contains("application/json")) {
                return JSONUtil.toBean(json, responseClass);
            } else {
                if (responseClass == String.class) {
                    return (T) json;
                } else {
                    log.error("response {}", json);
                    return null;
                }
            }
        } catch (IOException e) {
            log.error("okHttp request failed.", e);
        }
        return null;
    }


    @Override
    public String postJsonAndGetResponse(String url, Object requestBody, Map<String, String> headers) {
        logRequest(url, requestBody, headers, "post");
        Request.Builder requestBuilder = new Request.Builder().url(baseUrl + url).post(RequestBody.create(JSONUtil.toJsonStr(requestBody), JSON));
        commonHeaders(requestBuilder, headers);
        return send(requestBuilder.build(), String.class);
    }


    @Override
    public String getAndGetResponse(String url, Map<String, String> params, Map<String, String> headers) {
        logRequest(url, params, headers, "get");
        HttpUrl.Builder httpUrlBuilder = Objects.requireNonNull(HttpUrl.parse(baseUrl + url)).newBuilder();
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                httpUrlBuilder.addQueryParameter(entry.getKey(), entry.getValue());
            }
        }
        Request.Builder requestBuilder = new Request.Builder().url(httpUrlBuilder.build());
        commonHeaders(requestBuilder, headers);
        return send(requestBuilder.build(), String.class);
    }


    @Override
    public String postFormAndGetResponse(String url, Map<String, String> formData, Map<String, String> headers) {
        logRequest(url, formData, headers, "post");
        FormBody.Builder formBodyBuilder = new FormBody.Builder();
        if (formData != null && !formData.isEmpty()) {
            for (Map.Entry<String, String> entry : formData.entrySet()) {
                formBodyBuilder.add(entry.getKey(), entry.getValue());
            }
        }
        RequestBody formBody = formBodyBuilder.build();
        Request.Builder requestBuilder = new Request.Builder().url(baseUrl + url).post(formBody);
        commonHeaders(requestBuilder, headers);
        return send(requestBuilder.build(), String.class);
    }

    @Override
    public String postMultipartFormAndGetResponse(String url, MultipartBodyBuilder multipartBodyBuilder, Map<String, String> headers) {
        log.error("暂时未实现，推荐使用ProxyWebClient");
        return "";
    }


}
