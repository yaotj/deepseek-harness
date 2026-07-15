package com.chinasofti.huateng.micro.web.filter;

import com.chinasofti.huateng.micro.web.Webconfig;
import com.chinasofti.huateng.micro.web.utils.FilterUtils;
import com.chinasofti.huateng.micro.web.utils.JwtUtils;
import com.chinasofti.huateng.micro.web.utils.PathMatcher;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.jose4j.jwt.JwtClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Component
public class FirstFilter implements Filter {

    public static Logger log = LoggerFactory.getLogger(FirstFilter.class);

    @Autowired
    Webconfig webconfig;

    private boolean forbidden(HttpServletRequest httpServletRequest) {
        String[] urls = webconfig.getForbiddenUrls().split(",");
        return PathMatcher.match(Arrays.stream(urls).collect(Collectors.toList()), httpServletRequest.getRequestURI());
    }

    public String getOriginalUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder();
        url.append(request.getContextPath())
                .append(request.getRequestURI())
                .append(request.getQueryString() != null ? "?" + request.getQueryString() : "");
        return url.toString();
    }

    private boolean checkUrlSafe(HttpServletRequest httpServletRequest) {
        String url = getOriginalUrl(httpServletRequest);
        for (String unSafeUrlChar : webconfig.getUnSafeUrlCharsList()) {
            if (url.contains(unSafeUrlChar)) {
                log.warn("url={} contains unsafe char:{},fast return status=403 ", url, unSafeUrlChar);
                return false;
            }
        }
        return true;
    }

    private boolean checkToken(HttpServletRequest httpServletRequest, String token) {
        if (webconfig.getSkipCheckTokenUrls().equals("/**")) {
            return true;
        }
        String[] urls = webconfig.getSkipCheckTokenUrls().split(",");
        boolean needToCheck = PathMatcher.match(Arrays.stream(urls).collect(Collectors.toList()), httpServletRequest.getRequestURI());
        if (needToCheck) {
            if (token == null) {
                log.error("token is null");
                return false;
            }
            try {
                JwtClaims jwtClaims = JwtUtils.checkToken(token, JwtUtils.publicKey);
                if (jwtClaims == null) {
                    return false;
                }
            } catch (Exception e) {
                log.error("{}", e.getMessage(), e);
                return false;
            }
        }
        return true;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            Enumeration<String> headerNamesI = request.getHeaderNames();
            Map<String, String> headers = new HashMap<>();
            while (headerNamesI.hasMoreElements()) {
                String key = headerNamesI.nextElement().toLowerCase();
                headers.put(key, request.getHeader(key));
                MDC.put(key, request.getHeader(key));
            }

            if (webconfig.isEnableLogRequestInFilter()) {
                String request_url = request.getRequestURI();
                if (!request_url.startsWith("/actuator")) {
                    String queryString = request.getQueryString();
                    if (queryString != null) {
                        try {
                            queryString = "?" + URLDecoder.decode(queryString, "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            log.error("{}", e.getMessage(), e);
                        }
                    } else {
                        queryString = "";
                    }
                    log.info("receive request url={}{} method={} header={}", request_url, queryString, request.getMethod(), headers);
                }
            }

            if (!checkUrlSafe(request)) {
                FilterUtils.fastJsonResponse(servletResponse, 403, "{\"msg\":\"check url unSafe\"}");
                return;
            }

            if (!checkToken(request, headers.get("Authentication"))) {
                FilterUtils.fastJsonResponse(servletResponse, 403, "{\"msg\":\"check token error\"}");
                return;
            }

            if (forbidden(request)) {
                FilterUtils.fastJsonResponse(servletResponse, 403, "{\"msg\":\"ok\"}");
                log.warn("url={} is forbidden,fast return status=403 ", request.getRequestURI());
                return;
            }
        } else {
            log.warn("servletRequest not instanceof HttpServletRequest");
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }
}
