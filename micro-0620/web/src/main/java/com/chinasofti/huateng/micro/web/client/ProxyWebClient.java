package com.chinasofti.huateng.micro.web.client;

import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import javax.net.ssl.X509TrustManager;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ProxyWebClient extends AbstractMicroHttp<WebClient> {

    private WebClient getInitOkHttpClient(WebClient.Builder webClientBuilder) {
        ConnectionProvider provider = ConnectionProvider.builder("http").maxConnections(Integer.MAX_VALUE).build();
        HttpClient httpClient = HttpClient.create(provider);
        httpClient.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000);
        try {
            SslContext sslContext = SslContextBuilder.forClient()
                    .trustManager(new X509TrustManager() {
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
                    })
                    .build();
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
        setProxyHttpClient(getInitOkHttpClient(webClientBuilder));
    }

    private void logPostRequestJsonErr(Exception webClientException) {
        if (webClientException instanceof WebClientRequestException) {
            WebClientRequestException webClientRequestException = (WebClientRequestException) webClientException;
            log.error("webClient request failed, method=post,url={},headers={},msg={}", webClientRequestException.getUri(), webClientRequestException.getHeaders(), webClientRequestException.getMessage());
            return;
        }

        if (webClientException instanceof WebClientResponseException) {
            WebClientResponseException webClientResponseException = (WebClientResponseException) webClientException;
            log.error("webClient request failed, method=post,StatusText={},msg={}", webClientResponseException.getStatusText(), webClientResponseException.getMessage());
            return;
        }
        log.error(webClientException.getMessage());
    }

    private MultiValueMap<String, String> toMultiValueMap(Map<String, String> formData) {
        MultiValueMap<String, String> multiValueMap = new LinkedMultiValueMap<>();
        for (Map.Entry<String, String> entry : formData.entrySet()) {
            multiValueMap.add(entry.getKey(), entry.getValue());
        }
        return multiValueMap;
    }

    private void addAuthorizationToHeader(Map<String, String> headers) {
        if (headers == null) {
            headers = new HashMap<>();
        }
        try {
            String authorization = MDC.get("authorization");
            if (authorization != null && authorization.length() > 0) {
                headers.put("authorization", authorization);
            }
        } catch (Exception e) {
            log.error("{}", e.getMessage(), e);
        }
    }

    private <T> T handleResponse(Mono<ResponseEntity<T>> result) {
        ResponseEntity<T> res = null;
        try {
            res = result.block();
            logResponse(res.getStatusCode().value(), res.toString());
        } catch (Exception e) {
            log.error("{}", e.getMessage(), e);
            return null;
        }
        return res.getBody();
    }

    @Override
    public String postJsonAndGetResponse(String url, Object requestBody, Map<String, String> headers) {
        logRequest(url, requestBody, headers, "post");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = getProxyHttpClient().post();
        addAuthorizationToHeader(headers);
        if (headers != null && headers.size() > 0) {
            headers.forEach((k, v) -> {
                requestBodyUriSpec.header(k, v);
            });
        }
        Mono<ResponseEntity<String>> monoResponseEntity = requestBodyUriSpec.uri(url).accept(MediaType.APPLICATION_JSON).bodyValue(requestBody).retrieve()
                .onStatus(e -> e.isError(), resp -> {
                    return resp.createError();
                }).toEntity(String.class).doOnError(Exception.class, err -> {
                    logPostRequestJsonErr(err);
                });

        return handleResponse(monoResponseEntity);
    }

    public ResponseEntity<String> postJsonAndGetResponseEntity(String url, Object requestBody, Map<String, String> headers) {
        logRequest(url, requestBody, headers, "post");
        WebClient.RequestBodyUriSpec requestBodyUriSpec = getProxyHttpClient().post();
        addAuthorizationToHeader(headers);
        if (headers != null && headers.size() > 0) {
            headers.forEach((k, v) -> {
                requestBodyUriSpec.header(k, v);
            });
        }
        Mono<ResponseEntity<String>> monoResponseEntity = requestBodyUriSpec.uri(url).accept(MediaType.APPLICATION_JSON).bodyValue(requestBody).retrieve()
                .onStatus(e -> e.isError(), resp -> {
                    return resp.createError();
                }).toEntity(String.class).doOnError(Exception.class, err -> {
                    logPostRequestJsonErr(err);
                });

        ResponseEntity<String> res = null;
        try {
            res = monoResponseEntity.block();
            logResponse(res.getStatusCode().value(), res.toString());
        } catch (Exception e) {
            log.error("{}", e.getMessage());
            return null;
        }
        return res;
    }

    @Override
    public String getAndGetResponse(String url, Map<String, String> params, Map<String, String> headers) {
        logRequest(url, params, headers, "get");
        String get_url = UriComponentsBuilder.newInstance().queryParams(toMultiValueMap(params)).path(url).build().toString();
        addAuthorizationToHeader(headers);
        Mono<ResponseEntity<String>> monoResponseEntity = getProxyHttpClient().get().uri(get_url).accept(MediaType.APPLICATION_JSON).headers(new Consumer<HttpHeaders>() {
            @Override
            public void accept(HttpHeaders httpHeaders) {
                if (headers != null && headers.size() > 0) {
                    headers.forEach((k, v) -> {
                        httpHeaders.add(k, v);
                    });
                }
            }
        }).retrieve().onStatus(e -> e.isError(), resp -> {
            return resp.createError();
        }).toEntity(String.class).doOnError(Exception.class, err -> {
            logPostRequestJsonErr(err);
        });

        return handleResponse(monoResponseEntity);
    }


    @Override
    public String postFormAndGetResponse(String url, Map<String, String> formData, Map<String, String> headers) {
        logRequest(url, formData, headers, "post");
        addAuthorizationToHeader(headers);
        Mono<ResponseEntity<String>> result = getProxyHttpClient().post().uri(url).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(BodyInserters.fromFormData(toMultiValueMap(formData))).headers(new Consumer<HttpHeaders>() {
            @Override
            public void accept(HttpHeaders httpHeaders) {
                if (headers != null && headers.size() > 0) {
                    headers.forEach((k, v) -> {
                        httpHeaders.add(k, v);
                    });
                }
            }
        }).retrieve().onStatus(e -> e.isError(), resp -> {
            return resp.createError();
        }).toEntity(String.class).doOnError(Exception.class, err -> {
            logPostRequestJsonErr(err);
        });
        return handleResponse(result);
    }

    @Override
    public String postMultipartFormAndGetResponse(String url, MultipartBodyBuilder multipartBodyBuilder, Map<String, String> headers) {
        logRequest(url, null, headers, "post");
        addAuthorizationToHeader(headers);
        Mono<ResponseEntity<String>> result = getProxyHttpClient().post().uri(url)
                .body(BodyInserters.fromMultipartData(multipartBodyBuilder.build()))
                .headers(new Consumer<HttpHeaders>() {
                    @Override
                    public void accept(HttpHeaders httpHeaders) {
                        httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.MULTIPART_FORM_DATA_VALUE);
                        if (headers != null && headers.size() > 0) {
                            headers.forEach((k, v) -> {
                                httpHeaders.add(k, v);
                            });
                        }
                    }
                }).retrieve().onStatus(e -> e.isError(), resp -> {
                    return resp.createError();
                }).toEntity(String.class).doOnError(Exception.class, err -> {
                    logPostRequestJsonErr(err);
                });
        return handleResponse(result);
    }


}
