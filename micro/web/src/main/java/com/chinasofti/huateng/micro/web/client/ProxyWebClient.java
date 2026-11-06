package com.chinasofti.huateng.micro.web.client;

import cn.hutool.core.util.StrUtil;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;

public class ProxyWebClient extends AbstractMicroHttp<WebClient> {

    private static final boolean RETRY_ENABLED = true;

    private static final int MAX_RETRIES = 1;

    private WebClient getInitOkHttpClient(WebClient.Builder webClientBuilder, boolean h2c) {
        ConnectionProvider provider = ConnectionProvider.builder("http").maxConnections(Integer.MAX_VALUE).maxIdleTime(Duration.ofSeconds(25)).maxLifeTime(Duration.ofMinutes(8)).disposeTimeout(Duration.ofSeconds(5)).build();
        HttpClient httpClient = null;
        if (h2c) {
            httpClient = HttpClient.create(provider).option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000).protocol(HttpProtocol.H2C);
        } else {
            httpClient = HttpClient.create(provider);
            httpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000).protocol(HttpProtocol.HTTP11);
        }

        try {
            SslContext sslContext = SslContextBuilder.forClient().trustManager(new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {

                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[]{};
                }
            }).build();
            httpClient = httpClient.secure(sslSpec -> sslSpec.sslContext(sslContext));
        } catch (Exception e) {
            log.error("{}", e.getMessage(), e);
        }
        WebClient webClient = webClientBuilder.baseUrl(getBaseUrl()).defaultHeader("client", "spring WebClient").clientConnector(new ReactorClientHttpConnector(httpClient)).codecs(configure -> configure.defaultCodecs().maxInMemorySize(500 * 1024 * 1024)).build();
        return webClient;
    }

    /**
     * @param baseUrl value is like http://localhost:8080
     * @apiNote The simple of use:
     * <pre>{@code
     *     public ClientBWebClient(@Value("${other.service.b.url}") String baseUrl, @Value("${other.service.b.openLogger}") boolean openLogger, WebClient.Builder webClientBuilder) {
     *       super(baseUrl, openLogger, webClientBuilder);
     *     }
     * }</pre>
     */
    public ProxyWebClient(String baseUrl, boolean openLogger, WebClient.Builder webClientBuilder) {
        setOpenLogger(openLogger);
        setBaseUrl(baseUrl);
        setProxyHttpClient(getInitOkHttpClient(webClientBuilder, true));
    }

    public ProxyWebClient(String baseUrl, boolean h2c, boolean openLogger, WebClient.Builder webClientBuilder) {
        setOpenLogger(openLogger);
        setBaseUrl(baseUrl);
        setProxyHttpClient(getInitOkHttpClient(webClientBuilder, h2c));
    }

    private MultiValueMap<String, String> toMultiValueMap(Map<String, String> formData) {
        MultiValueMap<String, String> multiValueMap = new LinkedMultiValueMap<>();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            multiValueMap.add(entry.getKey(), entry.getValue());
        }
        return multiValueMap;
    }

    private <T> T handleResponse(Mono<ResponseEntity<T>> monoResponseEntity, HttpMethod method) {
        Mono<ResponseEntity<T>> pipeline = monoResponseEntity;
        if (RETRY_ENABLED) {
            pipeline = monoResponseEntity.retryWhen(WebClientRetryHelper.createNetworkRetryStrategy(method, MAX_RETRIES));
        }
        try {
            ResponseEntity<T> res = pipeline.block();
            if (res != null) {
                logResponse(res.getStatusCode().value(), res.toString());
                return res.getBody();
            }
        } catch (Exception e) {
            if (WebClientRetryHelper.isRetryableNetworkError(e)) {
                log.error("[RequestFailed] 重试{}次后仍然失败（网络异常）| 方法:{} | 异常:{}", MAX_RETRIES, method, e.getMessage(), e);
            } else {
                log.error("[RequestFailed] 请求失败（未重试）| 方法:{} | 异常:{}", method, e.getMessage(), e);
            }
        }
        return null;
    }

    @Override
    public String postJsonAndGetResponse(String url, Object requestBody, Map<String, String> headers) {
        logRequest(url, requestBody, headers, "post");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = getProxyHttpClient().post();

        String traceparentString = MDC.get("traceparent");
        if (traceparentString != null && traceparentString.length() > 0) {
            requestBodyUriSpec.header("traceparent", traceparentString);
        }
        if (headers != null && headers.size() > 0) {
            headers.forEach(requestBodyUriSpec::header);
        }

        Mono<ResponseEntity<String>> mono = requestBodyUriSpec.uri(url).accept(MediaType.APPLICATION_JSON).bodyValue(requestBody).retrieve().onStatus(HttpStatusCode::isError, resp -> resp.createError()).toEntity(String.class)
                .doOnError(Exception.class, err -> {
                });

        return handleResponse(mono, HttpMethod.POST);
    }

    public ResponseEntity<String> postJsonAndGetResponseEntity(String url, Object requestBody, Map<String, String> headers) {
        logRequest(url, requestBody, headers, "post");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = getProxyHttpClient().post();

        String traceparentString = MDC.get("traceparent");
        if (traceparentString != null && traceparentString.length() > 0) {
            requestBodyUriSpec.header("traceparent", traceparentString);
        }
        if (headers != null && headers.size() > 0) {
            headers.forEach(requestBodyUriSpec::header);
        }

        Mono<ResponseEntity<String>> mono = requestBodyUriSpec.uri(url).accept(MediaType.APPLICATION_JSON).bodyValue(requestBody).retrieve().onStatus(HttpStatusCode::isError, resp -> resp.createError()).toEntity(String.class).doOnError(Exception.class, err -> {
        });

        ResponseEntity<String> res = null;
        try {
            if (RETRY_ENABLED) {
                res = mono.retryWhen(WebClientRetryHelper.createNetworkRetryStrategy(HttpMethod.POST, MAX_RETRIES)).block();
            } else {
                res = mono.block();
            }
            if (res != null) {
                logResponse(res.getStatusCode().value(), res.toString());
            }
        } catch (Exception e) {
            if (WebClientRetryHelper.isRetryableNetworkError(e)) {
                log.error("[RequestFailed] 重试{}次后仍然失败（网络异常）| 方法:POST | 异常:{}", MAX_RETRIES, e.getMessage(), e);
            } else {
                log.error("[RequestFailed] 请求失败（未重试）| 方法:POST | 异常:{}", e.getMessage(), e);
            }
            return null;
        }
        return res;
    }

    @Override
    public String getAndGetResponse(String url, Map<String, String> params, Map<String, String> headers) {
        logRequest(url, params, headers, "get");
        String get_url = "";
        if (StrUtil.isEmpty(getBaseUrl())) {
            if (params == null || params.isEmpty()) {
                get_url = url;
            } else {
                get_url = UriComponentsBuilder.fromHttpUrl(url).queryParams(toMultiValueMap(params)).toUriString();
            }
        } else {
            get_url = UriComponentsBuilder.newInstance().queryParams(toMultiValueMap(params)).path(url).build().toString();
        }

        Mono<ResponseEntity<String>> mono = getProxyHttpClient().get().uri(get_url).accept(MediaType.APPLICATION_JSON).headers(httpHeaders -> {
            String traceparentString = MDC.get("traceparent");
            if (traceparentString != null && traceparentString.length() > 0) {
                httpHeaders.add("traceparent", traceparentString);
            }
            if (headers != null) {
                headers.forEach(httpHeaders::add);
            }
        }).retrieve().onStatus(HttpStatusCode::isError, resp -> resp.createError()).toEntity(String.class).doOnError(Exception.class, err -> {
        });

        return handleResponse(mono, HttpMethod.GET);
    }


    @Override
    public String postFormAndGetResponse(String url, Map<String, String> formData, Map<String, String> headers) {
        logRequest(url, formData, headers, "post");
        Mono<ResponseEntity<String>> mono = getProxyHttpClient().post().uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(BodyInserters.fromFormData(toMultiValueMap(formData))).headers(httpHeaders -> {
            String traceparentString = MDC.get("traceparent");
            if (traceparentString != null && traceparentString.length() > 0) {
                httpHeaders.add("traceparent", traceparentString);
            }
            if (headers != null) {
                headers.forEach(httpHeaders::add);
            }
        }).retrieve().onStatus(HttpStatusCode::isError, resp -> resp.createError()).toEntity(String.class).doOnError(Exception.class, err -> {
        });

        return handleResponse(mono, HttpMethod.POST);
    }

    @Override
    public String postMultipartFormAndGetResponse(String url, MultipartBodyBuilder multipartBodyBuilder, Map<String, String> headers) {
        logRequest(url, null, headers, "post");
        Mono<ResponseEntity<String>> mono = getProxyHttpClient().post().uri(url).body(BodyInserters.fromMultipartData(multipartBodyBuilder.build())).headers(httpHeaders -> {
            httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE);
            String traceparentString = MDC.get("traceparent");
            if (traceparentString != null && traceparentString.length() > 0) {
                httpHeaders.add("traceparent", traceparentString);
            }
            if (headers != null) {
                headers.forEach(httpHeaders::add);
            }
        }).retrieve().onStatus(HttpStatusCode::isError, resp -> resp.createError()).toEntity(String.class).doOnError(Exception.class, err -> {
        });

        return handleResponse(mono, HttpMethod.POST);
    }

    /**
     * desc:作为通知，不需要返回
     **/
    public void postNotifyByJson(String url, Object requestBody, Map<String, String> headers) {
        logRequest(url, requestBody, headers, "post");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = getProxyHttpClient().post();

        String traceparentString = MDC.get("traceparent");
        if (traceparentString != null && traceparentString.length() > 0) {
            requestBodyUriSpec.header("traceparent", traceparentString);
        }
        if (headers != null && headers.size() > 0) {
            headers.forEach(requestBodyUriSpec::header);
        }
        requestBodyUriSpec.uri(url).accept(MediaType.APPLICATION_JSON).bodyValue(requestBody).retrieve().bodyToMono(Void.class)
                .doOnError(e -> log.error("通知发送失败（不重试）: {}", e.getMessage(), e)).subscribe();
    }

}
