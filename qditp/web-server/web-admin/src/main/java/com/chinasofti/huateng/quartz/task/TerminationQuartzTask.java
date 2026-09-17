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

/** 解约申请确认任务：调 pay-sign-server 的 /internal/termination/process。 */
@Component("terminationQuartzTask")
public class TerminationQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(TerminationQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private static final DateTimeFormatter REFERENCE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 单次调度内最多调下游多少轮。 */
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

    /** 循环调下游直到本次 cutoff 之前没有待处理记录，并显式判定每一轮的结果。 */
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
        if (expired > 0) {
            log.error("解约申请批处理存在收口超时记录, request={}, expired={}", request, expired);
        }
        log.info("解约申请批处理完成, request={}, rounds={}, scanned={}, terminated={}, confirmed={}, rejected={}, expired={}, skipped={}",
                request, rounds, scanned, terminated, confirmed, rejected, expired, skipped);
    }
}
