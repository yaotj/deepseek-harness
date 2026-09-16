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
 * 对账内部接口专用异常处理，**必须存在**，否则分片上送的失败会被上游当成成功。
 *
 * <p>公共构件 {@code GlobalControllerExceptionHandler} 的兜底分支是
 * {@code @ExceptionHandler(Exception.class) @ResponseStatus(HttpStatus.OK)}，
 * 即**任何未被更具体处理器捕获的异常都返回 HTTP 200** + 一个 UUID {@code retCode}。
 * 而 {@code ReconClient} 只按 HTTP 状态码判成败，于是：</p>
 * <ul>
 *   <li>令牌校验失败（401）→ 上游看到 200，认为分片已被接收；</li>
 *   <li>分片落盘 IO 失败、哈希不一致拒收 → 同样是 200，源服务继续删本地分片。</li>
 * </ul>
 *
 * <p>2026-09-11 实测：无令牌请求 {@code GET /internal/recon/batches/{id}} 返回
 * {@code 200 {"retCode":"<uuid>","retMsg":null,"data":null}}。本类以
 * {@link Ordered#HIGHEST_PRECEDENCE} 抢在公共 advice 之前，把状态码还原成真实语义。
 * 作用范围用 {@code assignableTypes} 限定在 {@link ReconInternalController}，
 * 不影响其它模块与本模块将来可能新增的页面接口。</p>
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
