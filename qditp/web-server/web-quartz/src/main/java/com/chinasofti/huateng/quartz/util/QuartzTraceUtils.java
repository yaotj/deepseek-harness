package com.chinasofti.huateng.quartz.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.MDC;

public final class QuartzTraceUtils {

    public static final String TRACE_ID_KEY = "traceId";

    private static final String VLOGS_CAPTURE_KEY = "x-vlogs-capture";

    private static final String VLOGS_CAPTURE_VALUE = "1";

    private QuartzTraceUtils() {
    }


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

    public static Map<String, String> traceHeaders(String traceId) {
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, String> headers = new HashMap<>(4);
        headers.put("traceparent", "00-" + traceId + "-" + spanId + "-00");
        headers.put("X-Vlogs-Capture", "1");
        return headers;
    }


    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
