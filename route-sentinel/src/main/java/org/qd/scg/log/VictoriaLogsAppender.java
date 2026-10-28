package org.qd.scg.log;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

/**
 * 把日志以 JSON Lines 推送到 VictoriaLogs 的 logback Appender。
 *
 * <p>与 qd-itp 的 log4j2 {@code VictoriaLogsAppender} 保持一致的契约：
 * <ul>
 *   <li>{@code url} 为空（VLOGS_URL 未配）则自动禁用：不起线程、不丢日志；</li>
 *   <li>{@code url} 只给 {@code scheme://host:port} 时自动补全
 *       {@code /insert/jsonline?_stream_fields=app,host&_msg_field=_msg&_time_field=_time}；</li>
 *   <li>stream 字段只放低基数的 {@code app}/{@code host}；traceId 只进正文（MDC 白名单仅 traceId）；</li>
 *   <li>VLOGS_URL 端口 MUST 是 30032，不是 VictoriaLogs 默认 9428。</li>
 * </ul>
 *
 * <p>用 JDK 内置 HttpClient，无额外依赖；后台单线程批量发送，队列满则丢弃并限流告警，
 * 绝不让日志后端不可达反过来刷爆本地日志。
 */
public class VictoriaLogsAppender extends AppenderBase<ILoggingEvent> {

    private static final String DEFAULT_INSERT_PATH =
            "/insert/jsonline?_stream_fields=app,host&_msg_field=_msg&_time_field=_time";
    private static final long ERROR_LOG_INTERVAL_MILLIS = 60_000L;
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private String url;
    private String app = "route-sentinel";
    private String host;

    private String endpoint;
    private HttpClient httpClient;
    private BlockingQueue<String> queue;
    private Thread sender;
    private volatile boolean running;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong lastErrorLogAt = new AtomicLong();

    public void setUrl(String url) {
        this.url = url;
    }

    public void setApp(String app) {
        if (app != null && !app.isBlank()) {
            this.app = app;
        }
    }

    public void setHost(String host) {
        this.host = host;
    }

    private static String normalizeEndpoint(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.contains("/insert/")) {
            return trimmed;
        }
        return trimmed.endsWith("/")
                ? trimmed.substring(0, trimmed.length() - 1) + DEFAULT_INSERT_PATH
                : trimmed + DEFAULT_INSERT_PATH;
    }

    @Override
    public void start() {
        super.start();
        if (host == null || host.isBlank()) {
            host = System.getenv("HOSTNAME");
            if (host == null || host.isBlank()) {
                host = "unknown";
            }
        }
        endpoint = normalizeEndpoint(url);
        if (endpoint == null) {
            addWarn("VictoriaLogs appender '" + name + "' disabled: url is empty (set VLOGS_URL)");
            return;
        }
        queue = new ArrayBlockingQueue<>(8192);
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(java.time.Duration.ofMillis(2000))
                .build();
        running = true;
        sender = new Thread(this::runSender, "vlogs-sender");
        sender.setDaemon(true);
        sender.start();
        addInfo("VictoriaLogs appender '" + name + "' started, endpoint=" + endpoint);
    }

    @Override
    public void stop() {
        running = false;
        if (sender != null) {
            sender.interrupt();
        }
        flushRemaining();
        long total = dropped.get();
        if (total > 0) {
            addWarn("VictoriaLogs appender '" + name + "' dropped " + total + " events in total");
        }
        super.stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (!running) {
            return;
        }
        // 序列化在调用线程完成：logback 的 LoggingEvent 同样可能被复用/异步持有。
        String line = toJsonLine(event);
        if (line == null) {
            return;
        }
        if (!queue.offer(line)) {
            dropped.incrementAndGet();
        }
    }

    private String toJsonLine(ILoggingEvent event) {
        Map<String, String> mdc = event.getMDCPropertyMap();
        String traceId = mdc != null ? mdc.get("traceId") : null;
        IThrowableProxy tp = event.getThrowableProxy();
        String exception = tp != null ? ThrowableProxyUtil.asString(tp) : null;
        StringBuilder b = new StringBuilder();
        b.append('{');
        put(b, "_time", Instant.ofEpochMilli(event.getTimeStamp())
                .atZone(ZoneId.systemDefault()).format(TIME_FMT));
        put(b, "app", app);
        put(b, "host", host);
        put(b, "level", event.getLevel().levelStr);
        put(b, "logger", event.getLoggerName());
        put(b, "thread", event.getThreadName());
        if (traceId != null) {
            put(b, "traceId", traceId);
        }
        put(b, "_msg", event.getFormattedMessage());
        if (exception != null) {
            put(b, "exception", exception);
        }
        b.append('}');
        return b.toString();
    }

    private static void put(StringBuilder b, String key, String value) {
        if (b.length() > 1) {
            b.append(',');
        }
        b.append('"').append(escape(key)).append("\":\"");
        if (value != null) {
            b.append(escape(value));
        }
        b.append('"');
    }

    private static String escape(String s) {
        StringBuilder o = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> o.append("\\\"");
                case '\\' -> o.append("\\\\");
                case '\n' -> o.append("\\n");
                case '\r' -> o.append("\\r");
                case '\t' -> o.append("\\t");
                case '\b' -> o.append("\\b");
                case '\f' -> o.append("\\f");
                default -> {
                    if (c < 0x20) {
                        o.append(String.format("\\u%04x", (int) c));
                    } else {
                        o.append(c);
                    }
                }
            }
        }
        return o.toString();
    }

    private void runSender() {
        List<String> batch = new ArrayList<>(512);
        while (running) {
            try {
                String first = queue.poll(2000, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, 511);
                post(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                reportError(e.toString());
            } finally {
                batch.clear();
            }
        }
    }

    private void flushRemaining() {
        if (httpClient == null || queue.isEmpty()) {
            return;
        }
        List<String> batch = new ArrayList<>(512);
        while (queue.drainTo(batch, 512) > 0) {
            try {
                post(batch);
            } catch (Exception e) {
                reportError(e.toString());
                return;
            } finally {
                batch.clear();
            }
        }
    }

    private void post(List<String> lines) throws Exception {
        if (lines.isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder(lines.size() * 256);
        for (String line : lines) {
            body.append(line).append('\n');
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(java.time.Duration.ofMillis(5000))
                .header("Content-Type", "application/stream+json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        if (response.statusCode() / 100 != 2) {
            reportError("unexpected status " + response.statusCode());
        }
    }

    /** 失败只写 StatusLogger 且按分钟限流：VictoriaLogs 不可达时不能反过来刷爆本地日志。 */
    private void reportError(String reason) {
        long now = System.currentTimeMillis();
        long last = lastErrorLogAt.get();
        if (now - last >= ERROR_LOG_INTERVAL_MILLIS && lastErrorLogAt.compareAndSet(last, now)) {
            addWarn("VictoriaLogs push failed: " + reason + ", dropped so far=" + dropped.get());
        }
    }
}
