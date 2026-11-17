package com.chinasofti.huateng.rpc.route;

import com.chinasofti.huateng.micro.web.client.ProxyWebClient;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.Optional;


@Service
public class URLDynamicRouter extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(URLDynamicRouter.class);

    @Resource
    private Environment env;

    public URLDynamicRouter(@Value("${service.route.url.openLogger:true}") boolean openLogger, WebClient.Builder webClientBuilder) {
        super("", openLogger, webClientBuilder);
        log.info("init URLDynamicRouter openLogger={}", openLogger);
    }


    private String getServiceUrl(String service) {
        return env.getProperty("service." + service + ".url", service + "-service");
    }


    private String replaceService(String path) {
        String[] segments = Optional.ofNullable(path).orElse("").split("/+");
        if (segments.length < 2) {
            return path;
        }
        String serviceName = segments[1];
        String remainingPath = String.join("/", java.util.Arrays.copyOfRange(segments, 2, segments.length));
        return getServiceUrl(serviceName) + (remainingPath.isEmpty() ? "" : "/" + remainingPath);
    }


    public String postProxy(String url, Object requestBody, Map<String, String> headers) {
        Map<String, String> routeHeaders = createJsonHeaders();
        if (headers != null) {
            routeHeaders.putAll(headers);
        }
        return postJsonAndGetResponse(url, requestBody, routeHeaders);
    }


    public ResponseEntity<String> handlePostProxy(String url, Object requestBody, Map<String, String> headers) {
        Map<String, String> routeHeaders = createJsonHeaders();
        if (headers != null) {
            routeHeaders.putAll(headers);
        }
        return postJsonAndGetResponseEntity(replaceService(url), requestBody, routeHeaders);
    }

}
