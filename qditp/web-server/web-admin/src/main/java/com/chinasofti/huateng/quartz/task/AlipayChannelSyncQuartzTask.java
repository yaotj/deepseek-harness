package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 支付宝出行「支付通道同步」补偿任务：调 alipay-pay-sign-server 的
 * {@code POST /internal/alipay/channelSync/compensate}（ADR-D132）。
 *
 * <p>这是那套补偿的**唯一驱动源**：alipay-pay-sign-server 侧的
 * {@code ChannelSyncCompensationService} 刻意没有 {@code @Scheduled}（AGENTS.md §2.2.1），
 * 因此少了本任务 + 对应 {@code sys_job} 行，签约首推不可达的行会永久停在
 * {@code CHANNEL_SYNC_STATUS != 'SUCCESS'} 上、账户域缺一条支付通道且对上游完全不可见。
 * <b>「代码写了」不等于「补偿在跑」，判据是 {@code SYS_JOB_LOG} 有没有记录。</b></p>
 *
 * <p><b>NEVER 在本方法里循环调下游</b>：与销卡批处理（{@code AlipayTerminationQuartzTask}）不同，
 * 这里每轮扫多少由下游 {@code alipay.channel-sync.batch-size} 决定，排空靠 cron 的下一次触发。
 * 循环会让单次调度无上界地打对端 —— 而对端不可达正是这套补偿存在的原因。</p>
 */
@Component("alipayChannelSyncQuartzTask")
public class AlipayChannelSyncQuartzTask {

    private static final Logger log = LoggerFactory.getLogger(AlipayChannelSyncQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final AlipayPaySignClient alipayPaySignClient;

    public AlipayChannelSyncQuartzTask(AlipayPaySignClient alipayPaySignClient) {
        this.alipayPaySignClient = alipayPaySignClient;
    }

    /**
     * 前台调用目标填写 alipayChannelSyncQuartzTask.compensate() 时执行。
     *
     * <p>下游返回非 {@code 0000} 或空响应时 <b>MUST 抛异常</b>：不抛就会被
     * {@code AbstractQuartzJob.after()} 记成成功，于是「补偿一直在失败」在
     * {@code SYS_JOB_LOG} 里表现为一片绿。</p>
     */
    public void compensate() {
        QuartzTraceUtils.runWithTrace(this::compensateInTrace);
    }

    private void compensateInTrace(String traceId) {
        AlipayCommonResponse response =
                alipayPaySignClient.compensateChannelSync(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null) {
            throw new IllegalStateException("支付通道同步补偿接口未返回响应");
        }
        if (!SUCCESS_CODE.equals(response.getRetCode())) {
            throw new IllegalStateException("支付通道同步补偿失败, retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
        log.info("支付通道同步补偿完成, retMsg={}", response.getRetMsg());
    }
}
