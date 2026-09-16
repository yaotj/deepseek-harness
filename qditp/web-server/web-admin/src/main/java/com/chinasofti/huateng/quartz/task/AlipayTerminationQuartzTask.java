package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationRespDTO;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 支付宝出行销卡任务：调 alipay-pay-sign-server 的 /internal/alipay/termination/process。
 *
 * <p>业务口径：扫 {@code ALIPAY_TERMINATION_REQUEST} 里请求销卡（PENDING）的记录，
 * 通知支付中心业务关闭成功后把签约置 TERMINATED、登记置 COMPLETED。</p>
 *
 * <p>任务必须位于 Quartz 调用白名单包 com.chinasofti.huateng.quartz.task 下
 * （{@code Constants.JOB_WHITELIST_STR}）。</p>
 */
@Component("alipayTerminationQuartzTask")
public class AlipayTerminationQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(AlipayTerminationQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private static final DateTimeFormatter REFERENCE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 单次调度内最多调下游多少轮。下游每轮只取 BATCH_SIZE(200) 条，
     * 日级调度只调一轮的话积压超过 200 条就要拖到第二天，所以这里循环排空；
     * 上限用于防御下游一直返回「有记录但一条都没推进」的情况。
     */
    private static final int MAX_ROUNDS = 20;

    private final AlipayPaySignClient alipayPaySignClient;

    public AlipayTerminationQuartzTask(AlipayPaySignClient alipayPaySignClient) {
        this.alipayPaySignClient = alipayPaySignClient;
    }

    /**
     * 前台调用目标填写 alipayTerminationQuartzTask.cancelCard() 时执行，基准时间取当前时间。
     */
    public void cancelCard() {
        AlipayProcessTerminationReqDTO request = new AlipayProcessTerminationReqDTO();
        request.setReferenceTime(LocalDateTime.now().format(REFERENCE_TIME_FORMATTER));
        invoke(request);
    }

    /**
     * 指定基准时间的补跑入口。
     * 前台调用目标示例：alipayTerminationQuartzTask.cancelCard('20260907')。
     *
     * @param referenceTime 基准时间，yyyyMMdd 或 yyyyMMddHHmmss；只处理登记时间早于它的记录
     */
    public void cancelCard(String referenceTime) {
        AlipayProcessTerminationReqDTO request = new AlipayProcessTerminationReqDTO();
        request.setReferenceTime(referenceTime);
        invoke(request);
    }

    /**
     * 循环调下游直到本次 cutoff 之前没有待处理记录，并显式判定每一轮的结果。
     * 失败 MUST 抛异常：Quartz 只以异常判定失败，静默返回会让调度日志记成成功。
     *
     * <p>整个循环复用同一个 request，cutoff 在排空过程中固定不变，
     * **NEVER** 每轮重新取当前时间——否则边界会随耗时漂移。</p>
     */
    private void invoke(AlipayProcessTerminationReqDTO request) {
        QuartzTraceUtils.runWithTrace(traceId -> invokeInTrace(request, traceId));
    }

    private void invokeInTrace(AlipayProcessTerminationReqDTO request, String traceId) {
        int rounds = 0;
        int scanned = 0;
        int terminated = 0;
        int failed = 0;
        int skipped = 0;
        while (rounds < MAX_ROUNDS) {
            AlipayProcessTerminationRespDTO response = alipayPaySignClient.processAlipayTermination(request,
                    QuartzTraceUtils.traceHeaders(traceId));
            if (response == null) {
                throw new IllegalStateException("支付宝出行销卡批处理接口未返回响应, request=" + request
                        + ", round=" + (rounds + 1));
            }
            if (!SUCCESS_CODE.equals(response.getResultCode())) {
                throw new IllegalStateException("支付宝出行销卡批处理失败, request=" + request + ", round=" + (rounds + 1)
                        + ", resultCode=" + response.getResultCode() + ", resultMsg=" + response.getResultMsg());
            }
            rounds++;
            scanned += response.getScanned();
            terminated += response.getTerminated();
            failed += response.getFailed();
            skipped += response.getSkipped();
            if (response.getScanned() == 0) {
                break;
            }
            // 本轮扫到了记录但一条都没推进状态，再调下去只会拿到同一批。
            int progressed = response.getTerminated() + response.getFailed();
            if (progressed == 0) {
                log.warn("支付宝出行销卡批处理本轮无进展，提前结束, request={}, round={}, scanned={}, skipped={}",
                        request, rounds, response.getScanned(), response.getSkipped());
                break;
            }
        }
        if (rounds >= MAX_ROUNDS) {
            log.error("支付宝出行销卡批处理达到轮次上限仍未排空, request={}, rounds={}, scanned={}", request, rounds, scanned);
        }
        // failed 非 0 说明有登记被判终态失败（当前唯一原因是签约信息不存在），需人工核对，
        // 这不是本次调用失败，因此单独用 error 级别留痕而不抛异常。
        if (failed > 0) {
            log.error("支付宝出行销卡批处理存在失败记录, request={}, failed={}", request, failed);
        }
        log.info("支付宝出行销卡批处理完成, request={}, rounds={}, scanned={}, terminated={}, failed={}, skipped={}",
                request, rounds, scanned, terminated, failed, skipped);
    }
}
