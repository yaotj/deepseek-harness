package org.qd.scg.filter;

import java.io.IOException;

import org.qd.scg.common.RequestConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 缓存 {@code application/x-www-form-urlencoded} 请求体，并使其参数对后续链路可见。
 *
 * <p>route-sentinel 的分发谓语需要从 {@code bizData} 中解析 {@code thirdUserId}。
 * BOM/TVM 等设备域请求使用 POST + form-urlencoded，参数位于 body 而非 query string，
 * 导致 {@code ServerRequest.param("bizData")} 读不到。本过滤器在请求入口处缓存 body 并
 * 重建 {@code getParameter} 族方法，既不影响下游转发，又能让分发逻辑正确读取 form 参数。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FormDataCacheFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FormDataCacheFilter.class);
    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String contentType = request.getContentType();
        String uri = request.getRequestURI();
        if (contentType != null && contentType.toLowerCase().startsWith(FORM_CONTENT_TYPE)) {
            CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request);
            String bizData = wrapped.getParameter("bizData");
            String thirdUserId = extractThirdUserId(bizData);
            wrapped.setAttribute(RequestConstants.BIZDATA_ATTR, bizData);
            wrapped.setAttribute(RequestConstants.THIRD_USER_ID_ATTR, thirdUserId);
            if (log.isDebugEnabled()) {
                log.debug("FormDataCacheFilter 命中 path={} contentLength={} bizData={} thirdUserId={}",
                        uri, wrapped.getCachedBody().length, bizData, thirdUserId);
            }
            filterChain.doFilter(wrapped, response);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("FormDataCacheFilter 跳过 path={} contentType={}", uri, contentType);
            }
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 快速字符串扫描，从 {@code bizData} JSON 串中提取 {@code thirdUserId}，失败返回 null。
     */
    private String extractThirdUserId(String bizData) {
        if (bizData == null || bizData.isBlank()) {
            return null;
        }
        String key = "\"thirdUserId\"";
        int keyIdx = bizData.indexOf(key);
        if (keyIdx < 0) {
            return null;
        }
        int colonIdx = bizData.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) {
            return null;
        }
        int len = bizData.length();
        int start = colonIdx + 1;
        while (start < len) {
            char c = bizData.charAt(start);
            if (c != ' ' && c != '\t') {
                break;
            }
            start++;
        }
        if (start >= len) {
            return null;
        }
        char quote = bizData.charAt(start);
        if (quote != '"' && quote != '\'') {
            return null;
        }
        int end = bizData.indexOf(quote, start + 1);
        if (end < 0) {
            return null;
        }
        return bizData.substring(start + 1, end);
    }
}
