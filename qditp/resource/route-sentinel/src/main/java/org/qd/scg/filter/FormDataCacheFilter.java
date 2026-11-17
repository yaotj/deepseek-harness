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
            String thirdUserId = extractValue(bizData, "\"thirdUserId\"");
            String cardId = extractValue(bizData, "\"cardId\"");
            String orderNo = extractValue(bizData, "\"orderNo\"");
            String randomFact = extractValue(bizData, "\"randomFact\"");
            wrapped.setAttribute(RequestConstants.BIZDATA_ATTR, bizData);
            wrapped.setAttribute(RequestConstants.THIRD_USER_ID_ATTR, thirdUserId);
            wrapped.setAttribute(RequestConstants.CARD_ID_ATTR, cardId);
            wrapped.setAttribute(RequestConstants.ORDER_NO_ATTR, orderNo);
            wrapped.setAttribute(RequestConstants.RANDOM_FACT_ATTR, randomFact);
            if (log.isDebugEnabled()) {
                log.debug("FormDataCacheFilter 命中 path={} contentLength={} bizData={} thirdUserId={} cardId={} orderNo={} randomFact={}",
                        uri, wrapped.getCachedBody().length, bizData, thirdUserId, cardId, orderNo, randomFact);
            }
            filterChain.doFilter(wrapped, response);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("FormDataCacheFilter 跳过 path={} contentType={}", uri, contentType);
            }
            filterChain.doFilter(request, response);
        }
    }


    private String extractValue(String bizData, String key) {
        if (bizData == null || bizData.isBlank()) {
            return null;
        }
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
