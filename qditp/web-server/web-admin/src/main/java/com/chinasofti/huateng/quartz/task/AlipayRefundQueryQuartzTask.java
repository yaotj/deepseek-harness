package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 支付宝出行「退款回查」补偿任务：调 alipay-pay-sign-server 的
 * {@code POST /internal/alipay/refund/compensateQuery}。
 *
 * <p>这是那套补偿的**唯一驱动源**：alipay-pay-sign-server 侧的
 * {@code RefundQueryCompensationService} 刻意没有 {@code @Scheduled}（AGENTS.md §2.2.1），
 * 因此少了本任务 + 对应 {@code sys_job} 行，退款申请里「支付中心没答 / 拒绝」那支落下的
 * {@code REFUND_STATUS='PROCESSING'} 明细**没有任何人再看一眼**，只能人工核。
 * <b>「代码写了」不等于「补偿在跑」，判据是 {@code SYS_JOB_LOG} 有没有记录。</b>
 *
 * <p><b>cron 间隔 MUST 大于下游的静默期 5 分钟</b>（现为 10 分钟一轮，与 pay-sign 侧
 * `sys_job` 305「退款回查补偿」同口径，2026-09-21 由 122 改号为 305）：间隔更短会在正常退款回调还没到达时就反复回查同一行。
 *
 * <p><b>NEVER 在本方法里循环调下游</b>：每轮扫多少由下游 {@code alipay.refund-query.batch-size}
 * 决定，排空靠 cron 的下一次触发。循环会让单次调度无上界地打支付中心 ——
 * 而支付中心答不上来正是这套补偿存在的原因。
 */
@Component("alipayRefundQueryQuartzTask")
public class AlipayRefundQueryQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(AlipayRefundQueryQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final AlipayPaySignClient alipayPaySignClient;

    public AlipayRefundQueryQuartzTask(AlipayPaySignClient alipayPaySignClient) {
        this.alipayPaySignClient = alipayPaySignClient;
    }

    /**
     * 前台调用目标填写 alipayRefundQueryQuartzTask.compensate() 时执行。
     *
     * <p>下游返回非 {@code 0000} 或空响应时 <b>MUST 抛异常</b>：不抛就会被
     * {@code AbstractQuartzJob.after()} 记成成功，于是「补偿一直打不通」在 {@code SYS_JOB_LOG}
     * 里表现为一片绿。<b>但「本轮收口 0 条」不是失败</b> —— 下游对那种情况照样返 {@code 0000}，
     * 条数只写在 {@code retMsg} 里，NEVER 在这里改成按条数判成败。
     */
    public void compensate() {
        QuartzTraceUtils.runWithTrace(this::compensateInTrace);
    }

    private void compensateInTrace(String traceId) {
        AlipayCommonResponse response =
                alipayPaySignClient.compensateRefundQuery(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException("退款回查补偿接口未返回响应");
        }
        if (!SUCCESS_CODE.equals(response.getRetCode())) {
            throw new IllegalStateException("退款回查补偿失败, retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
        log.info("退款回查补偿完成, retMsg={}", response.getRetMsg());
    }
}
