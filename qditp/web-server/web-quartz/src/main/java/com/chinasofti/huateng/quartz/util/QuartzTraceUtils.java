package com.chinasofti.huateng.quartz.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.slf4j.MDC;

/**
 * Quartz 任务的链路追踪辅助方法。
 *
 * <p>本类是 {@link AbstractQuartzJob} 生成的 traceId 与「任务方法往下游传 trace 头」之间的唯一桥梁。
 * 抽出来的原因：{@code web-admin} 的 7 个任务类此前各自持有一份**逐字节相同**的
 * {@code newTraceId()} / {@code traceHeaders()}，改一处采样标记要同步改七处，漏改无法在编译期发现。</p>
 *
 * <p><b>NEVER 在任务类里再自行生成 traceId。</b>Quartz 调度进来时 traceId 已由
 * {@link AbstractQuartzJob#before} 放入 MDC，并由 {@code after()} 追加进
 * {@code sys_job_log.job_message}；另生成一个会让前台「调度日志」里的 traceId 与实际发给下游的对不上，
 * 运维按前台值去日志系统检索将一无所获。</p>
 */
public final class QuartzTraceUtils {

    /**
     * MDC 中链路追踪标识的键名。
     *
     * <p>MUST 用驼峰 traceId：这是 Micrometer Tracing correlation 的字段名，
     * 也是各服务日志 pattern（{@code %X{traceId}}）与 VictoriaLogs appender 取值用的键，
     * 写成 trace_id 会与全链路对不上。</p>
     */
    public static final String TRACE_ID_KEY = "traceId";

    /**
     * 日志全量采集标记在 MDC 中的键名。
     *
     * <p>**MUST 全小写。**入向请求时这个键由 micro 的 {@code FirstFilter} 把请求头
     * {@code X-Vlogs-Capture} 小写后放入；本类为「本端主动发起」的链路补同一个键，
     * 好让两侧用同一条 log4j2 过滤规则。写成驼峰会匹配不上
     * （`log4j2-linux.xml` 的 {@code ThreadContextMapFilter} 只认小写）。</p>
     */
    private static final String VLOGS_CAPTURE_KEY = "x-vlogs-capture";

    private static final String VLOGS_CAPTURE_VALUE = "1";

    private QuartzTraceUtils() {
    }

    /**
     * 在 traceId 上下文中执行任务体，并把 traceId 交给回调。
     *
     * <p>Quartz 路径下直接复用 MDC 里已有的值；非 Quartz 路径（本地手工调用 / 单测）自己生成、
     * 自己清理，**MUST NOT** 顺手清掉别人放进去的值——否则 {@code AbstractQuartzJob.after()}
     * 就取不到 traceId 写库了。</p>
     *
     * <p>同时往 MDC 放 {@link #VLOGS_CAPTURE_KEY}={@code 1}：Root 只把 WARN 及以上推给
     * VictoriaLogs，任务正常跑完全是 INFO，不加这个标记则**日志系统里查不到本次调度的任何记录**
     * （2026-09-08 实测：按 sys_job_log 的 traceId 检索返回 0 条）。加了之后本端 INFO 全量上报，
     * 前台「执行日志」才能看到「调了谁、发了什么头、对端回了什么」。</p>
     *
     * <p>该标记 **MUST 在 finally 里还原**：Quartz worker 线程是池化复用的，不清理会让后续
     * 任何任务的 INFO 日志都被误当成「需要全量采集的链路」推上去。</p>
     *
     * @param action 任务体，参数是本次调度的 traceId，可多次调用 {@link #traceHeaders(String)}
     *               为每一轮下游请求生成独立 spanId
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

    /**
     * 组装 W3C traceparent 头，交给下游服务续接链路。
     *
     * <p>末段采样标记固定为 {@code 00}（不采样）：下游
     * {@code management.tracing.sampling.probability=0} 只为在 MDC 里拿到 traceId 供日志用，
     * 传 {@code 01} 会让对端按「父 span 已采样」把 span 推给 OTLP collector
     * （{@code management.otlp.tracing.endpoint}），该地址可达性尚未实测，不可达时会持续刷导出失败日志。
     * 确认要看 trace 拓扑时再改成 {@code 01}。</p>
     *
     * <p>{@code X-Vlogs-Capture} 是日志全量采集标记，供下游 log4j2 配置里 VictoriaLogs appender 的
     * {@code ThreadContextMapFilter} 匹配：带此标记的链路日志（含 DEBUG）全量推送 VictoriaLogs，
     * 其余请求仍只推 WARN 及以上。依赖 micro 的 {@code FirstFilter} 把请求头**小写**后放进 MDC，
     * 因此对端匹配的 MDC 键是 {@code x-vlogs-capture}，**NEVER** 在 log4j2 配置里写成驼峰或原样大小写。</p>
     *
     * <p>**NEVER 传自定义 {@code traceId} 头**：{@code FirstFilter} 会把所有请求头原样塞进 MDC，
     * 自定义头会与 Micrometer 写入的 traceId 争抢同一个键，行为取决于两者执行先后，不可控。</p>
     */
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
