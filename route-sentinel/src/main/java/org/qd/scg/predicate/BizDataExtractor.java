package org.qd.scg.predicate;

import java.util.Optional;

import org.qd.scg.common.RequestConstants;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class BizDataExtractor {

    private static final String KEY = "\"thirdUserId\"";

    private final ObjectMapper objectMapper;

    public BizDataExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Fast path: direct字符串扫描 "thirdUserId"。失败返回 null。
     *
     * <p>优先顺序：
     * <ol>
     *   <li>FormDataCacheFilter 写入的请求属性 {@code route-sentinel.thirdUserId}；</li>
     *   <li>{@code ServerRequest.param("bizData")}（query string）；</li>
     *   <li>{@code HttpServletRequest.getParameter("bizData")}（form body）。</li>
     * </ol>
     */
    public String extractThirdUserIdFast(ServerRequest request) {
        Object cached = request.attribute(RequestConstants.THIRD_USER_ID_ATTR).orElse(null);
        if (cached instanceof String thirdUserId && !thirdUserId.isBlank()) {
            return thirdUserId;
        }
        String bizData = request.param("bizData").orElse(null);
        if (bizData == null || bizData.isBlank()) {
            bizData = request.servletRequest().getParameter("bizData");
        }
        if (bizData == null || bizData.isBlank()) {
            return null;
        }
        String value = fastExtract(bizData);
        return (value == null || value.isBlank()) ? null : value;
    }

    private String fastExtract(String bizData) {
        int keyIdx = bizData.indexOf(KEY);
        if (keyIdx < 0) {
            return null;
        }
        int colonIdx = bizData.indexOf(':', keyIdx + KEY.length());
        if (colonIdx < 0) {
            return null;
        }
        int len = bizData.length();
        int start = colonIdx + 1;
        while (start < len) {
            char c = bizData.charAt(start);
            if (c != ' ' && c != '\t') break;
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
