package com.chinasofti.huateng.micro.web.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Set;

public class WebClientRetryHelper {

    public static Logger log = LoggerFactory.getLogger(WebClientRetryHelper.class);

    private static final Set<String> RETRYABLE_ERROR_MESSAGES = Set.of(
            "Connection prematurely closed BEFORE response",
            "Connection prematurely closed DURING response",
            "Connection reset by peer",
            "Broken pipe",
            "Connection refused",
            "Connection timed out",
            "No route to host",
            "Unexpected end of stream"
    );
    private static final Set<Class<? extends Throwable>> RETRYABLE_EXCEPTION_TYPES = Set.of(
            reactor.netty.http.client.PrematureCloseException.class,
            io.netty.channel.ChannelException.class,
            java.io.IOException.class,
            java.net.ConnectException.class,
            java.net.SocketException.class
    );
    private static final Set<Class<? extends Throwable>> NON_RETRYABLE_EXCEPTIONS = Set.of(
            org.springframework.web.reactive.function.client.WebClientResponseException.class,
            java.util.concurrent.TimeoutException.class
    );

    public static boolean isIdempotent(HttpMethod method) {
        return method == HttpMethod.GET || method == HttpMethod.HEAD
                || method == HttpMethod.PUT || method == HttpMethod.DELETE
                || method == HttpMethod.OPTIONS;
    }

    public static boolean isRetryableNetworkError(Throwable throwable) {
        if (throwable == null) return false;
        for (Class<? extends Throwable> clazz : NON_RETRYABLE_EXCEPTIONS) {
            if (clazz.isInstance(throwable)) return false;
        }
        for (Class<? extends Throwable> clazz : RETRYABLE_EXCEPTION_TYPES) {
            if (clazz.isInstance(throwable)) return true;
        }
        String message = throwable.getMessage();
        if (message != null) {
            String lower = message.toLowerCase();
            for (String m : RETRYABLE_ERROR_MESSAGES) {
                if (lower.contains(m.toLowerCase())) return true;
            }
        }
        return isRetryableNetworkError(throwable.getCause());
    }

    public static boolean isBeforeRequestSent(Throwable throwable) {
        String msg = throwable != null ? throwable.getMessage() : "";
        if (msg == null) msg = "";
        return msg.contains("BEFORE response")
                || msg.contains("Connection refused")
                || msg.contains("Connection reset")
                || msg.contains("No route to host");
    }

    public static Retry createNetworkRetryStrategy(HttpMethod method, int maxRetries) {
        return Retry.backoff(maxRetries, Duration.ofMillis(200))
                .maxBackoff(Duration.ofSeconds(2))
                .jitter(0.5)
                .filter(throwable -> {
                    boolean retryable = isRetryableNetworkError(throwable);
                    if (!retryable) return false;

                    if (isIdempotent(method)) {
                        return true;
                    }
                    return isBeforeRequestSent(throwable);
                })
                .doBeforeRetry(retrySignal -> {
                    log.warn("[NetworkRetry] 第{}次重试 | 方法:{} | 原因:{}",
                            retrySignal.totalRetries() + 1,
                            method,
                            retrySignal.failure().getMessage());
                })
                .onRetryExhaustedThrow((spec, signal) -> {
                    return signal.failure();
                });
    }
}
