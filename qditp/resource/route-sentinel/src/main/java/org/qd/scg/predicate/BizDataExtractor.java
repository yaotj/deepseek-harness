package org.qd.scg.predicate;

import java.util.Optional;

import org.qd.scg.common.RequestConstants;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class BizDataExtractor {

    private static final String THIRD_USER_ID_KEY = "\"thirdUserId\"";
    private static final String CARD_ID_KEY = "\"cardId\"";
    private static final String ORDER_NO_KEY = "\"orderNo\"";
    private static final String RANDOM_FACT_KEY = "\"randomFact\"";

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
        String bizData = extractBizData(request);
        if (bizData == null || bizData.isBlank()) {
            return null;
        }
        String value = fastExtract(bizData, THIRD_USER_ID_KEY);
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * Fast path: direct字符串扫描 "cardId"。失败返回 null。
     *
     * <p>优先顺序与 {@link #extractThirdUserIdFast(ServerRequest)} 一致。
     */
    public String extractCardIdFast(ServerRequest request) {
        Object cached = request.attribute(RequestConstants.CARD_ID_ATTR).orElse(null);
        if (cached instanceof String cardId && !cardId.isBlank()) {
            return cardId;
        }
        String bizData = extractBizData(request);
        if (bizData == null || bizData.isBlank()) {
            return null;
        }
        String value = fastExtract(bizData, CARD_ID_KEY);
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * Fast path: direct字符串扫描 "orderNo"（TVM 取票/出票上报）。失败返回 null。
     *
     * <p>优先顺序与 {@link #extractThirdUserIdFast(ServerRequest)} 一致。
     */
    public String extractOrderNoFast(ServerRequest request) {
        Object cached = request.attribute(RequestConstants.ORDER_NO_ATTR).orElse(null);
        if (cached instanceof String orderNo && !orderNo.isBlank()) {
            return orderNo;
        }
        String bizData = extractBizData(request);
        if (bizData == null || bizData.isBlank()) {
            return null;
        }
        String value = fastExtract(bizData, ORDER_NO_KEY);
        return (value == null || value.isBlank()) ? null : value;
    }

    /**
     * Fast path: direct字符串扫描 "randomFact"（取票二维码随机因子）。失败返回 null。
     *
     * <p>优先顺序与 {@link #extractThirdUserIdFast(ServerRequest)} 一致。
     */
    public String extractRandomFactFast(ServerRequest request) {
        Object cached = request.attribute(RequestConstants.RANDOM_FACT_ATTR).orElse(null);
        if (cached instanceof String randomFact && !randomFact.isBlank()) {
            return randomFact;
        }
        String bizData = extractBizData(request);
        if (bizData == null || bizData.isBlank()) {
            return null;
        }
        String value = fastExtract(bizData, RANDOM_FACT_KEY);
        return (value == null || value.isBlank()) ? null : value;
    }

    private String extractBizData(ServerRequest request) {
        String bizData = request.param("bizData").orElse(null);
        if (bizData == null || bizData.isBlank()) {
            bizData = request.servletRequest().getParameter("bizData");
        }
        return bizData;
    }

    private String fastExtract(String bizData, String key) {
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
