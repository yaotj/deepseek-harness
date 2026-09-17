package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.recon.ReconClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** recon-server 的 Quartz 调用桥接任务：日终对账。 */
@Component("reconQuartzTask")
public class ReconQuartzTask
{
    private static final Logger log = LoggerFactory.getLogger(ReconQuartzTask.class);

    private final ReconClient reconClient;

    public ReconQuartzTask(ReconClient reconClient)
    {
        this.reconClient = reconClient;
    }

    /** 前台调用目标填写 reconQuartzTask.runDailyBatch() 时执行。 */
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
