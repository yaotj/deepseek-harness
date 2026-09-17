package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.account.AccountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** account-server 的 Quartz 调用桥接任务。 */
@Component("accountQuartzTask")
public class AccountQuartzTask
{
    private static final Logger log = LoggerFactory.getLogger(AccountQuartzTask.class);

    private final AccountClient accountClient;

    public AccountQuartzTask(AccountClient accountClient)
    {
        this.accountClient = accountClient;
    }

    /** 前台调用目标填写 accountQuartzTask.invokeDemo() 时执行。 */
    public void invokeDemo()
    {
        QuartzTraceUtils.runWithTrace(this::invokeOnce);
    }

    private void invokeOnce(String traceId)
    {
        CommonResult response = accountClient.quartzDemo(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null)
        {
            throw new IllegalStateException("account-server Quartz 联调接口未返回响应");
        }
        if (!"0000".equals(response.getRetCode()))
        {
            throw new IllegalStateException("account-server Quartz 联调失败: retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
        log.info("account-server Quartz 联调调用成功, retMsg={}", response.getRetMsg());
    }

    /** 前台调用目标填写 accountQuartzTask.compensatePhoneSignSync() 时执行。 */
    public void compensatePhoneSignSync()
    {
        QuartzTraceUtils.runWithTrace(this::compensatePhoneSignSyncOnce);
    }

    private void compensatePhoneSignSyncOnce(String traceId)
    {
        CommonResult response = accountClient.compensatePhoneSignSync(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null)
        {
            throw new IllegalStateException("account-server 签约展示账号补偿接口未返回响应");
        }
        if (!"0000".equals(response.getRetCode()))
        {
            throw new IllegalStateException("account-server 签约展示账号补偿失败: retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
        log.info("account-server 签约展示账号补偿调用成功, retMsg={}", response.getRetMsg());
    }
}
