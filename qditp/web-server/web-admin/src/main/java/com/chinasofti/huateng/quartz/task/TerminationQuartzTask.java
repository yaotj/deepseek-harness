package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.paysign.ProcessTerminationReqDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 解约申请确认任务：调 pay-sign-server 的 /internal/termination/process。
 *
 * <p>业务口径：乘客 APP 申请解约支付方式后，按规定满 N 天（默认 4 天）才确认解约。
 * 延迟天数由 pay-sign-server 的 {@code termination.confirm-delay-days} 控制，本任务只传基准时间，
 * **NEVER** 在这里再写一份天数，否则两处配置会漂移。</p>
 *
 * <p>任务必须位于 Quartz 调用白名单包 com.chinasofti.huateng.quartz.task 下
 * （{@code Constants.JOB_WHITELIST_STR}）。</p>
 */
@Component("terminationQuartzTask")
public class TerminationQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(TerminationQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private static final DateTimeFormatter REFERENCE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 单次调度内最多调下游多少轮。
     * 下游 processTermination 每轮对 PENDING 与 SCANNING 各只取 BATCH_SIZE(200) 条，
     * 本任务是日级调度，只调一轮的话积压超过 400 条就要拖到第二天，
     * 而积压越久 SCANNING 越接近收口超时阈值。所以这里循环排空。
     * 上限存在的意义是防御：下游若一直返回「有记录但一条都没推进」，不能无限打下去。
     */
    private static final int MAX_ROUNDS = 20;

    private final PaySignClient paySignClient;

    public TerminationQuartzTask(PaySignClient paySignClient) {
        this.paySignClient = paySignClient;
    }

    /**
     * 前台调用目标填写 terminationQuartzTask.confirmTermination() 时执行。
     * 基准时间取当前时间，延迟天数用 pay-sign-server 的配置默认值。
     */
    public void confirmTermination() {
        ProcessTerminationReqDTO request = new ProcessTerminationReqDTO();
        request.setReferenceTime(LocalDateTime.now().format(REFERENCE_TIME_FORMATTER));
        invoke(request);
    }

    /**
     * 指定基准时间与延迟天数的补跑入口。
     * 前台调用目标示例：terminationQuartzTask.confirmTermination('20260907', 4)。
     *
     * @param referenceTime 基准时间，yyyyMMdd 或 yyyyMMddHHmmss
     * @param delayDays     规定天数，截止点 = 基准时间 - 该天数
     */
    public void confirmTermination(String referenceTime, int delayDays) {
        ProcessTerminationReqDTO request = new ProcessTerminationReqDTO();
        request.setReferenceTime(referenceTime);
        request.setDelayDays(delayDays);
        invoke(request);
    }

    /**
     * 循环调下游直到本次 cutoff 之前没有待处理记录，并显式判定每一轮的结果。
     * 失败 MUST 抛异常：Quartz 只以异常判定失败，静默返回会让调度日志记成成功。
     *
     * <p>整个循环复用同一个 request，因此 cutoff 在排空过程中固定不变，
     * **NEVER** 每轮重新取当前时间——否则边界会随耗时漂移，刚好卡在边界上的申请会被漏掉。</p>
     */
    private void invoke(ProcessTerminationReqDTO request) {
        // Quartz 调度进来时 traceId 已由 AbstractQuartzJob.before() 放入 MDC，
        // 并会被 after() 写进 sys_job_log.job_message，这里直接复用，MUST NOT 另生成一个——
        // 否则前台调度日志里的 traceId 与实际发给 pay-sign 的对不上。
        String traceId = MDC.get("traceId");
        boolean ownTraceId = traceId == null || traceId.isEmpty();
        if (ownTraceId) {
            // 非 Quartz 路径（本地手工调用 / 单测）兜底，自己生成并自己清理。
            traceId = newTraceId();
            MDC.put("traceId", traceId);
        }
        try {
            invokeInTrace(request, traceId);
        } finally {
            if (ownTraceId) {
                MDC.remove("traceId");
            }
        }
    }

    /**
     * 生成 32 位小写 hex 的 traceId，仅用于非 Quartz 调用路径的兜底。
     */
    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 组装 W3C traceparent 头，交给 pay-sign 续接链路。
     *
     * <p>末段采样标记固定为 {@code 00}（不采样）：pay-sign 侧
     * {@code management.tracing.sampling.probability=0} 只为在 MDC 里拿到 traceId 供日志用，
     * 传 {@code 01} 会让对端按「父 span 已采样」把 span 推给 OTLP collector
     * （{@code management.otlp.tracing.endpoint}），该地址可达性尚未实测，不可达时会持续刷导出失败日志。
     * 确认要看 trace 拓扑时再改成 {@code 01}。</p>
     */
    private static Map<String, String> traceHeaders(String traceId) {
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, String> headers = new HashMap<>(2);
        headers.put("traceparent", "00-" + traceId + "-" + spanId + "-00");
        return headers;
    }

    private void invokeInTrace(ProcessTerminationReqDTO request, String traceId) {
        int rounds = 0;
        int scanned = 0;
        int terminated = 0;
        int confirmed = 0;
        int rejected = 0;
        int expired = 0;
        int skipped = 0;
        while (rounds < MAX_ROUNDS) {
            ProcessTerminationRespDTO response = paySignClient.processTermination(request, traceHeaders(traceId));
            if (response == null) {
                throw new IllegalStateException("解约申请批处理接口未返回响应, request=" + request + ", round=" + (rounds + 1));
            }
            if (!SUCCESS_CODE.equals(response.getResultCode())) {
                throw new IllegalStateException("解约申请批处理失败, request=" + request + ", round=" + (rounds + 1)
                        + ", resultCode=" + response.getResultCode() + ", resultMsg=" + response.getResultMsg());
            }
            rounds++;
            scanned += response.getScanned();
            terminated += response.getTerminated();
            confirmed += response.getConfirmed();
            rejected += response.getRejected();
            expired += response.getExpired();
            skipped += response.getSkipped();
            if (response.getScanned() == 0) {
                break;
            }
            // 本轮扫到了记录但一条都没推进状态，再调下去只会拿到同一批。
            // 停下来告警等下一次调度，避免把一批卡住的记录刷满 MAX_ROUNDS。
            int progressed = response.getTerminated() + response.getConfirmed()
                    + response.getRejected() + response.getExpired();
            if (progressed == 0) {
                log.warn("解约申请批处理本轮无进展，提前结束, request={}, round={}, scanned={}, skipped={}",
                        request, rounds, response.getScanned(), response.getSkipped());
                break;
            }
        }
        if (rounds >= MAX_ROUNDS) {
            log.error("解约申请批处理达到轮次上限仍未排空, request={}, rounds={}, scanned={}", request, rounds, scanned);
        }
        // expired 非 0 说明有申请因收口超时被判失败，需人工到支付中心核对协议真实状态，
        // 这不是本次调用失败，因此单独用 error 级别留痕而不抛异常。
        if (expired > 0) {
            log.error("解约申请批处理存在收口超时记录, request={}, expired={}", request, expired);
        }
        log.info("解约申请批处理完成, request={}, rounds={}, scanned={}, terminated={}, confirmed={}, rejected={}, expired={}, skipped={}",
                request, rounds, scanned, terminated, confirmed, rejected, expired, skipped);
    }
}
