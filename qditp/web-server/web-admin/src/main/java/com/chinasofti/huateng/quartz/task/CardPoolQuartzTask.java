package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.cardpool.CardPoolActionResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.cardpool.CardPoolClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** card-pool-server 的 Quartz 调用桥接任务。 */
@Component("cardPoolQuartzTask")
public class CardPoolQuartzTask
{
    private static final Logger log = LoggerFactory.getLogger(CardPoolQuartzTask.class);

    private final CardPoolClient cardPoolClient;

    public CardPoolQuartzTask(CardPoolClient cardPoolClient)
    {
        this.cardPoolClient = cardPoolClient;
    }

    /** 前台调用目标填写 cardPoolQuartzTask.runMaintenance() 时执行。 */
    public void runMaintenance()
    {
        QuartzTraceUtils.runWithTrace(this::invokeOnce);
    }

    /** 下游是**受理式**接口：提交给单线程维护池后立刻返回，ACC 申请、FTP 下载、十万行入库都在 */
    private void invokeOnce(String traceId)
    {
        CardPoolActionResult result = cardPoolClient.runMaintenance(QuartzTraceUtils.traceHeaders(traceId));
        if (result == null)
        {
            throw new IllegalStateException("card-pool-server 卡池维护接口未返回响应");
        }
        if (!result.isSuccess())
        {
            throw new IllegalStateException("card-pool-server 卡池维护调用失败: outcome=" + result.getOutcome()
                    + ", message=" + result.getMessage());
        }
        log.info("card-pool-server 卡池维护受理成功, {}", result.getMessage());
    }
}
