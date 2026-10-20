package org.qd.scg.predicate;

import java.util.Optional;

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
     * Fast path: direct string scan for "thirdUserId". Returns null on failure.
     * Zero allocation on the happy path (caller must handle null).
     */
    public String extractThirdUserIdFast(ServerRequest request) {
        String bizData = request.param("bizData").orElse(null);
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
