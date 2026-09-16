package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.paysign.ProcessTerminationReqDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
     * <p><b>第二个参数 MUST 是 {@code Integer} 而不是 {@code int}</b>：
     * {@link com.chinasofti.huateng.quartz.util.JobInvokeUtil#getMethodParams} 把不带后缀的数字
     * 一律解析成 {@code Integer.class}（只有 {@code L} 后缀→Long、{@code D}→Double、
     * 引号→String、true/false→Boolean），而它随后用 {@code getClass().getMethod(名, 类型数组)}
     * 反射查找 —— <b>{@code getMethod} 按精确类型匹配、不做自动装箱</b>。因此签名写 {@code int}
     * 时从后台调用必抛 {@code NoSuchMethodException: confirmTermination(java.lang.String,
     * java.lang.Integer)}，且 {@code 0} / {@code 0L} / {@code 0D} 全都对不上、无从绕过。
     * 2026-09-14 实测到该异常（job 4，job_log_id=5034）。
     * <b>本包内新增带数字参数的任务方法 MUST 一律用包装类型。</b></p>
     *
     * @param referenceTime 基准时间，yyyyMMdd 或 yyyyMMddHHmmss
     * @param delayDays     规定天数，截止点 = 基准时间 - 该天数
     */
    public void confirmTermination(String referenceTime, Integer delayDays) {
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
        QuartzTraceUtils.runWithTrace(traceId -> invokeInTrace(request, traceId));
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
            ProcessTerminationRespDTO response = paySignClient.processTermination(request,
                    QuartzTraceUtils.traceHeaders(traceId));
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
