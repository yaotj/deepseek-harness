package com.chinasofti.huateng.micro.web.filter;

import com.chinasofti.huateng.micro.web.Webconfig;
import com.chinasofti.huateng.micro.web.utils.HttpRequestWrapper;
import com.chinasofti.huateng.micro.web.utils.HttpResponseWrapper;
import com.chinasofti.huateng.micro.web.utils.PathMatcher;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.stream.Collectors;

@Order(Ordered.HIGHEST_PRECEDENCE + 4)
@Component
public class BodyCacheFilter implements Filter {

    public static Logger log = LoggerFactory.getLogger(BodyCacheFilter.class);

    @Autowired
    Webconfig webconfig;

    private boolean checkSkipUrl(ServletRequest servletRequest) {
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
            if (httpServletRequest.getRequestURI().startsWith("/actuator")) {
                return true;
            }
            String[] urls = webconfig.getLogBodyCacheUrls().split(",");
            return !PathMatcher.match(Arrays.stream(urls).collect(Collectors.toList()), httpServletRequest.getRequestURI());
        } else {
            return true;
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (!((HttpServletRequest) servletRequest).getRequestURI().startsWith("/druid")) {
            ServletRequest request = servletRequest;
            ServletResponse response = servletResponse;
            if (webconfig.isEnableBodyCacheFilter()) {
                if (!checkSkipUrl(request)) {
                    if (servletRequest instanceof HttpServletRequest) {
                        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
                        HttpRequestWrapper httpRequestWrapper = new HttpRequestWrapper(httpServletRequest, webconfig.isReadInputStreamInMultipartReq());
                        request = httpRequestWrapper;
                    }
                    if (servletResponse instanceof HttpServletResponse) {
                        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
                        HttpResponseWrapper httpResponseWrapper = new HttpResponseWrapper(httpServletResponse);
                        response = httpResponseWrapper;
                    }
                }
            }
            filterChain.doFilter(request, response);
            if (webconfig.isEnableBodyCacheFilter()) {
                if (!checkSkipUrl(request)) {
                    int status = 200;
                    if (servletResponse instanceof HttpServletResponse) {
                        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
                        status = httpServletResponse.getStatus();
                    }
                    String request_url = "";
                    if (request instanceof HttpServletRequest) {
                        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
                        request_url = httpServletRequest.getRequestURI();
                    }
                    String content = MessageFormat.format("request_url={0} requestParams:{1} and response.status={2} response body:{3}",
                            request_url,
                            HttpRequestWrapper.getRequestBody(request),
                            status,
                            HttpResponseWrapper.getResponseBody(response, webconfig.getLogResponseMaxSize()));
                    content = content.replaceAll("\r\n", "").replaceAll("\n", "");
                    log.info(content);
                }
            }
        } else {
            filterChain.doFilter(servletRequest, servletResponse);
        }

    }

}
