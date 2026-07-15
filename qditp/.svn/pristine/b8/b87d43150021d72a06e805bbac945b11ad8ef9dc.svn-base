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

/**
 * desc: url动态路由
 **/
@Service
public class URLDynamicRouter extends ProxyWebClient {

    public static Logger log = LoggerFactory.getLogger(URLDynamicRouter.class);

    @Resource
    private Environment env;

    public URLDynamicRouter(@Value("${service.route.url.openLogger:true}") boolean openLogger, WebClient.Builder webClientBuilder) {
        super("", openLogger, webClientBuilder);
        log.info("init URLDynamicRouter openLogger={}", openLogger);
    }

    /**
     * desc:从配置文件中 service.服务名.url 属性获取对应的http://host:port
     **/
    private String getServiceUrl(String service) {
        return env.getProperty("service." + service + ".url", service + "-service");
    }

    /**
     * 替换路径中的代理服务名为实际地址
     *
     * @param path 原始路径，格式如 "/服务名/资源路径"
     * @return 替换后的完整URL
     */
    private String replaceService(String path) {
        String[] segments = Optional.ofNullable(path).orElse("").split("/+");
        if (segments.length < 2) {
            return path;
        }
        String serviceName = segments[1];
        String remainingPath = String.join("/", java.util.Arrays.copyOfRange(segments, 2, segments.length));
        return getServiceUrl(serviceName) + (remainingPath.isEmpty() ? "" : "/" + remainingPath);
    }

    /**
     * desc: 本服务(该方法的位置）->代理服务->被代理的服务
     * </br> url 形如：http://host:port/被代理服务的名称/book/getAll。host:port为代理服务的信息，为被代理服务的名称在代理服务中配置有对应的http://host:port用于url重构
     **/
    public String postProxy(String url, Object requestBody, Map<String, String> headers) {
        Map<String, String> routeHeaders = createJsonHeaders();
        if (headers != null) {
            routeHeaders.putAll(headers);
        }
        return postJsonAndGetResponse(url, requestBody, routeHeaders);
    }

    /**
     * desc: 客户端->代理服务(该方法的位置)->被代理的服务
     * </br> url 形如：/被代理服务的名称/book/getAll,第一段在代理服务中配置有对应的http://host:port用于url重构
     **/
    public ResponseEntity<String> handlePostProxy(String url, Object requestBody, Map<String, String> headers) {
        Map<String, String> routeHeaders = createJsonHeaders();
        if (headers != null) {
            routeHeaders.putAll(headers);
        }
        return postJsonAndGetResponseEntity(replaceService(url), requestBody, routeHeaders);
    }

}
