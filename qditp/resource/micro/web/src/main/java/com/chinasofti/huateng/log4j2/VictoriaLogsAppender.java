package com.chinasofti.huateng.log4j2;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 把日志以 JSON Lines 推送到 VictoriaLogs 的 log4j2 Appender。
 *
 * <p>序列化交给内嵌的 {@code Layout}（用 {@code JsonTemplateLayout} + event template），
 * 本类只负责「批量攒 + HTTP 发送」。字段名、MDC 白名单都在 template 里定义，
 * 加字段无需改 Java 代码、无需重新构建本模块。</p>
 *
 * <p>设计约束（与本项目已发生的生产事故直接相关，改动前务必先读）：
 * <ul>
 *   <li>业务线程只做「序列化 + 入有界队列」，绝不在业务线程上发 HTTP。全服务开了
 *       {@code spring.threads.virtual.enabled=true}，在虚拟线程上做阻塞 IO 会 pin 载体线程。</li>
 *   <li>发送线程是**平台线程**（{@code Thread.ofPlatform()}），阻塞它不影响虚拟线程调度。</li>
 *   <li>队列满时丢弃并计数，**绝不阻塞业务线程**——日志管道不能反噬业务。</li>
 *   <li>内部异常只走 log4j2 StatusLogger（{@code LOGGER}），绝不用 slf4j，避免日志递归。
 *       注意各模块 log4j2 配置多为 {@code status="off"}，这些自述日志默认看不到；
 *       判断是否启用请看 {@code vlogs-sender} 线程是否存在。</li>
 *   <li>本类放在 micro/web 只是「能力下放」：log4j2 仅在配置里出现 {@code <VictoriaLogs>}
 *       时才实例化插件，光有类不会起线程、不会发请求，因此对未配置的模块零影响。</li>
 * </ul>
 */
@Plugin(name = "VictoriaLogs", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public final class VictoriaLogsAppender extends AbstractAppender {

    /** 未显式给出 /insert/ 路径时补全的默认路径。stream 字段必须低基数，traceId 只能进正文。 */
    private static final String DEFAULT_INSERT_PATH =
            "/insert/jsonline?_stream_fields=app,host&_msg_field=_msg&_time_field=_time";

    private static final long ERROR_LOG_INTERVAL_MILLIS = 60_000L;

    private final String endpoint;
    private final int batchSize;
    private final long flushIntervalMillis;
    private final Duration requestTimeout;
    private final Duration connectTimeout;
    private final BlockingQueue<String> queue;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong lastErrorLogAt = new AtomicLong();

    private volatile boolean running;
    private HttpClient httpClient;
    private Thread sender;

    private VictoriaLogsAppender(String name, Filter filter, Layout<? extends Serializable> layout,
                                 boolean ignoreExceptions, Property[] properties,
                                 String endpoint, int queueCapacity, int batchSize, long flushIntervalMillis,
                                 long connectTimeoutMillis, long requestTimeoutMillis) {
        super(name, filter, layout, ignoreExceptions, properties);
        this.endpoint = endpoint;
        this.batchSize = batchSize;
        this.flushIntervalMillis = flushIntervalMillis;
        this.connectTimeout = Duration.ofMillis(connectTimeoutMillis);
        this.requestTimeout = Duration.ofMillis(requestTimeoutMillis);
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
    }

    @PluginFactory
    public static VictoriaLogsAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginAttribute("url") String url,
            @PluginAttribute(value = "queueCapacity", defaultInt = 8192) int queueCapacity,
            @PluginAttribute(value = "batchSize", defaultInt = 512) int batchSize,
            @PluginAttribute(value = "flushIntervalMillis", defaultLong = 2000L) long flushIntervalMillis,
            @PluginAttribute(value = "connectTimeoutMillis", defaultLong = 2000L) long connectTimeoutMillis,
            @PluginAttribute(value = "requestTimeoutMillis", defaultLong = 5000L) long requestTimeoutMillis,
            @PluginAttribute(value = "ignoreExceptions", defaultBoolean = true) boolean ignoreExceptions,
            @PluginElement("Layout") Layout<? extends Serializable> layout,
            @PluginElement("Filter") Filter filter) {

        if (name == null || name.isBlank()) {
            LOGGER.error("VictoriaLogs appender requires a name");
            return null;
        }
        if (layout == null) {
            LOGGER.error("VictoriaLogs appender '{}' requires a Layout, use JsonTemplateLayout", name);
            return null;
        }
        return new VictoriaLogsAppender(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY,
                normalizeEndpoint(url), Math.max(64, queueCapacity), Math.max(1, batchSize),
                Math.max(200L, flushIntervalMillis), connectTimeoutMillis, requestTimeoutMillis);
    }

    /**
     * 只给出 {@code scheme://host:port} 时补全 jsonline 路径与查询参数。
     * 缺失 {@code _stream_fields} 会让每种字段组合都变成一条 stream，直接打爆索引，因此不允许裸 base URL 透传。
     */
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
        if (endpoint == null) {
            LOGGER.warn("VictoriaLogs appender '{}' disabled: url is empty (set VLOGS_URL)", getName());
            return;
        }
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        running = true;
        sender = Thread.ofPlatform()
                .name("vlogs-sender")
                .daemon(true)
                .unstarted(this::runSender);
        sender.start();
        LOGGER.info("VictoriaLogs appender '{}' started, endpoint={}", getName(), endpoint);
    }

    @Override
    public boolean stop(long timeout, TimeUnit timeUnit) {
        running = false;
        if (sender != null) {
            sender.interrupt();
        }
        flushRemaining();
        long total = dropped.get();
        if (total > 0) {
            LOGGER.warn("VictoriaLogs appender '{}' dropped {} events in total", getName(), total);
        }
        return super.stop(timeout, timeUnit);
    }

    @Override
    public void append(LogEvent event) {
        if (!running) {
            return;
        }
        // 序列化必须在调用线程完成：LogEvent 可能被复用，异步持有会读到串改后的内容。
        String line = new String(getLayout().toByteArray(event), StandardCharsets.UTF_8);
        // JsonTemplateLayout 默认在每条事件后追加换行，这里剥掉，由 post() 统一按 jsonline 规则拼接。
        line = line.stripTrailing();
        if (line.isEmpty()) {
            return;
        }
        if (!queue.offer(line)) {
            dropped.incrementAndGet();
        }
    }

    private void runSender() {
        List<String> batch = new ArrayList<>(batchSize);
        while (running) {
            try {
                String first = queue.poll(flushIntervalMillis, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, batchSize - 1);
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
        List<String> batch = new ArrayList<>(batchSize);
        while (queue.drainTo(batch, batchSize) > 0) {
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

    private void post(List<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        StringBuilder body = new StringBuilder(lines.size() * 256);
        for (String line : lines) {
            body.append(line).append('\n');
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(requestTimeout)
                .header("Content-Type", "application/stream+json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                reportError("unexpected status " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            reportError(e.toString());
        }
    }

    /** 失败只写 StatusLogger 且按分钟限流：VictoriaLogs 不可达时不能反过来刷爆本地日志。 */
    private void reportError(String reason) {
        long now = System.currentTimeMillis();
        long last = lastErrorLogAt.get();
        if (now - last >= ERROR_LOG_INTERVAL_MILLIS && lastErrorLogAt.compareAndSet(last, now)) {
            LOGGER.warn("VictoriaLogs push failed: {}, dropped so far={}", reason, dropped.get());
        }
    }
}
