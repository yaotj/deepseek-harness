package com.chinasofti.huateng.quartz.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.MDC;

/** Quartz 任务的链路追踪辅助方法。 */
public final class QuartzTraceUtils {

    /** MDC 中链路追踪标识的键名。 */
    public static final String TRACE_ID_KEY = "traceId";

    /** 日志全量采集标记在 MDC 中的键名。 */
    private static final String VLOGS_CAPTURE_KEY = "x-vlogs-capture";

    private static final String VLOGS_CAPTURE_VALUE = "1";

    private QuartzTraceUtils() {
    }

    /**
     * 在 traceId 上下文中执行任务体，并把 traceId 交给回调。
     *
     * @param action 任务体，参数是本次调度的 traceId，可多次调用 {@link #traceHeaders(String)}
     */
    public static void runWithTrace(Consumer<String> action) {
        String traceId = MDC.get(TRACE_ID_KEY);
        boolean ownTraceId = traceId == null || traceId.isEmpty();
        if (ownTraceId) {
            traceId = newTraceId();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        String previousCapture = MDC.get(VLOGS_CAPTURE_KEY);
        MDC.put(VLOGS_CAPTURE_KEY, VLOGS_CAPTURE_VALUE);
        try {
            action.accept(traceId);
        } finally {
            if (previousCapture == null) {
                MDC.remove(VLOGS_CAPTURE_KEY);
            } else {
                MDC.put(VLOGS_CAPTURE_KEY, previousCapture);
            }
            if (ownTraceId) {
                MDC.remove(TRACE_ID_KEY);
            }
        }
    }

    /** 组装 W3C traceparent 头，交给下游服务续接链路。 */
    public static Map<String, String> traceHeaders(String traceId) {
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, String> headers = new HashMap<>(4);
        headers.put("traceparent", "00-" + traceId + "-" + spanId + "-00");
        headers.put("X-Vlogs-Capture", "1");
        return headers;
    }

    /**
     * 生成 32 位小写 hex 的 traceId，与 {@link AbstractQuartzJob#before} 口径一致。
     */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
