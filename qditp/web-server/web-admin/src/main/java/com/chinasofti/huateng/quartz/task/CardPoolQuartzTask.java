package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.model.cardpool.CardPoolActionResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.cardpool.CardPoolClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("cardPoolQuartzTask")
public class CardPoolQuartzTask
{
    private static final Logger log = LoggerFactory.getLogger(CardPoolQuartzTask.class);

    private final CardPoolClient cardPoolClient;

    public CardPoolQuartzTask(CardPoolClient cardPoolClient)
    {
        this.cardPoolClient = cardPoolClient;
    }

    public void runMaintenance()
    {
        QuartzTraceUtils.runWithTrace(this::invokeOnce);
    }

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
