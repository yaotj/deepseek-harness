package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.recon.ReconClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * recon-server 的 Quartz 调用桥接任务：日终对账。
 *
 * <p>任务类必须位于白名单包 {@code com.chinasofti.huateng.quartz.task} 下
 * （{@code web-common/.../constant/Constants.java} 的 {@code JOB_WHITELIST_STR}），
 * 放别的包前台直接报「违规」。</p>
 *
 * <p><b>本任务是日终对账的唯一调度源。</b>recon-server 已按用户 2026-09-11 的要求删掉
 * {@code @EnableScheduling}、一个 {@code @Scheduled} 都没有，账期何时跑、跑几次完全由
 * {@code sys_job} 的 cron 决定（默认每天 02:30 一次）。**NEVER 在 recon-server 那边再加回
 * {@code @Scheduled}**，两套调度源互不知情，改 cron 时只改一处就会重复建批次与重复投递。</p>
 */
@Component("reconQuartzTask")
public class ReconQuartzTask
{
    private static final Logger log = LoggerFactory.getLogger(ReconQuartzTask.class);

    private final ReconClient reconClient;

    public ReconQuartzTask(ReconClient reconClient)
    {
        this.reconClient = reconClient;
    }

    /**
     * 前台调用目标填写 reconQuartzTask.runDailyBatch() 时执行。
     *
     * <p>触发 recon-server 跑完一整轮日终对账：建批次 → 下发三个源抽取 → 轮询收齐 →
     * 生成四类文件 → FTP 投递。账期由下游按 {@code recon.orchestration.window-offset-days}
     * 自行推算（T-2 日），**本任务不传参**，因此重复触发是幂等的。</p>
     *
     * <p>下游同步跑完才返回，单次耗时实测约 60 秒、上限由下游
     * {@code recon.orchestration.run-timeout-millis}（默认 4 分钟）控制。因此**执行策略建议
     * 配「禁止并发」**，避免前台「执行一次」与 cron 重叠时排队占用 Quartz worker
     * （下游也有进程内拒绝，会直接返回失败）。</p>
     *
     * <p>失败 MUST 抛异常 —— {@code sys_job_log} 的成功/失败判定只看有没有异常抛出，
     * 只打日志会让「对账没跑成」在调度日志里显示为成功。</p>
     */
    public void runDailyBatch()
    {
        QuartzTraceUtils.runWithTrace(this::runDailyBatchOnce);
    }

    private void runDailyBatchOnce(String traceId)
    {
        CommonResult response = reconClient.runDailyBatch(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null)
        {
            throw new IllegalStateException("recon-server 日终对账接口未返回响应");
        }
        if (!"0000".equals(response.getRetCode()))
        {
            throw new IllegalStateException("recon-server 日终对账失败: retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
        log.info("recon-server 日终对账调用成功, retMsg={}", response.getRetMsg());
    }
}
