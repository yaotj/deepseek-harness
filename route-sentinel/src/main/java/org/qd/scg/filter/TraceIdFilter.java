package org.qd.scg.filter;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, java.io.IOException {
        String traceId = request.getHeader(RouteConstants.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put("traceId", traceId);
        try {
            HeaderRequestWrapper wrapped = new HeaderRequestWrapper(request, RouteConstants.TRACE_ID_HEADER, traceId);
            chain.doFilter(wrapped, response);
        } finally {
            MDC.remove("traceId");
        }
    }

    private static class HeaderRequestWrapper extends HttpServletRequestWrapper {
        private final String headerName;
        private final String headerValue;

        HeaderRequestWrapper(HttpServletRequest request, String headerName, String headerValue) {
            super(request);
            this.headerName = headerName;
            this.headerValue = headerValue;
        }

        @Override
        public String getHeader(String name) {
            if (headerName.equalsIgnoreCase(name)) {
                return headerValue;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (headerName.equalsIgnoreCase(name)) {
                return Collections.enumeration(List.of(headerValue));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            if (names.stream().noneMatch(n -> headerName.equalsIgnoreCase(n))) {
                names.add(headerName);
            }
            return Collections.enumeration(names);
        }
    }
}
