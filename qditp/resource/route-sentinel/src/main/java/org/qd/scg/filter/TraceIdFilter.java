package org.qd.scg.filter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qd.scg.common.RouteConstants;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Pattern TRACE_PARENT_PATTERN =
            Pattern.compile("^([0-9a-fA-F]{2})-([0-9a-fA-F]{32})-([0-9a-fA-F]{16})-([0-9a-fA-F]{2})$");
    private static final SecureRandom SPAN_ID_RANDOM = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, java.io.IOException {
        String traceId = request.getHeader(RouteConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put("traceId", traceId);
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put(RouteConstants.TRACE_ID_HEADER.toLowerCase(Locale.ROOT), traceId);
            headers.putAll(buildTraceHeaders(request, traceId));
            HeaderRequestWrapper wrapped = new HeaderRequestWrapper(request, headers);
            chain.doFilter(wrapped, response);
        } finally {
            MDC.remove("traceId");
        }
    }

    /**
     * 生成 W3C traceparent 与 B3 单头，使下游 micro 服务可按 traceparent/b3 连续链路。
     * 上游已带合法 traceparent 时保留原值，只补齐缺失的 b3；X-Request-Id 保留向后兼容。
     */
    private static Map<String, String> buildTraceHeaders(HttpServletRequest request, String traceId) {
        Map<String, String> headers = new HashMap<>();
        String incomingTraceparent = request.getHeader(RouteConstants.TRACE_PARENT_HEADER);
        Matcher matcher = incomingTraceparent == null ? null : TRACE_PARENT_PATTERN.matcher(incomingTraceparent);
        String traceIdHex;
        String spanIdHex;
        if (matcher != null && matcher.matches()) {
            traceIdHex = matcher.group(2).toLowerCase(Locale.ROOT);
            spanIdHex = matcher.group(3).toLowerCase(Locale.ROOT);
        } else {
            traceIdHex = normalizeTraceId(traceId);
            spanIdHex = newSpanId();
            headers.put(RouteConstants.TRACE_PARENT_HEADER, "00-" + traceIdHex + "-" + spanIdHex + "-01");
        }
        String incomingB3 = request.getHeader(RouteConstants.B3_HEADER);
        if (incomingB3 == null || incomingB3.isBlank()) {
            headers.put(RouteConstants.B3_HEADER, traceIdHex + "-" + spanIdHex + "-1");
        }
        return headers;
    }

    private static String normalizeTraceId(String value) {
        String hex = value.replace("-", "").toLowerCase(Locale.ROOT);
        if (hex.matches("[0-9a-f]{32}")) {
            return hex;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return toHex(Arrays.copyOf(digest, 16));
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private static String newSpanId() {
        byte[] bytes = new byte[8];
        SPAN_ID_RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    private static class HeaderRequestWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> overrides;

        HeaderRequestWrapper(HttpServletRequest request, Map<String, String> overrides) {
            super(request);
            this.overrides = overrides;
        }

        @Override
        public String getHeader(String name) {
            String value = overrides.get(name.toLowerCase(Locale.ROOT));
            return value != null ? value : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String value = overrides.get(name.toLowerCase(Locale.ROOT));
            return value != null ? Collections.enumeration(List.of(value)) : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            for (String key : overrides.keySet()) {
                if (names.stream().noneMatch(key::equalsIgnoreCase)) {
                    names.add(key);
                }
            }
            return Collections.enumeration(names);
        }
    }
}
