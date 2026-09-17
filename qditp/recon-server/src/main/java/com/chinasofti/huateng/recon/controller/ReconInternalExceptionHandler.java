package com.chinasofti.huateng.recon.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * 对账内部接口专用异常处理：把公共兜底 advice 的 HTTP 200 还原成真实状态码。
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ReconInternalController.class)
public class ReconInternalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ReconInternalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        log.warn("recon internal request rejected, status={} reason={}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("msg", ex.getReason() == null ? "rejected" : ex.getReason()));
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Map<String, String>> handleOther(Throwable ex) {
        log.error("recon internal request failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("msg", ex.getClass().getSimpleName()));
    }
}
