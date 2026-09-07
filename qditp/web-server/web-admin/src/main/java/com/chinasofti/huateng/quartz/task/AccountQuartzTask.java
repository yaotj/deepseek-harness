package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.rpc.account.AccountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * account-server 的 Quartz 调用桥接任务。
 *
 * <p>任务必须位于 Quartz 调用白名单包 com.chinasofti.huateng.quartz.task 下，
 * Quartz 仅调用本 Bean；跨服务调用由 AccountClient 完成。</p>
 */
@Component("accountQuartzTask")
public class AccountQuartzTask
{
    private static final Logger log = LoggerFactory.getLogger(AccountQuartzTask.class);

    private final AccountClient accountClient;

    public AccountQuartzTask(AccountClient accountClient)
    {
        this.accountClient = accountClient;
    }

    /**
     * 前台调用目标填写 accountQuartzTask.invokeDemo() 时执行。
     */
    public void invokeDemo()
    {
        CommonResult response = accountClient.quartzDemo();
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
}
