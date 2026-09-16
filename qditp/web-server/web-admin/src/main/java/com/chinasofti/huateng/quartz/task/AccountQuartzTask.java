package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
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
     *
     * <p>traceId 由 {@link QuartzTraceUtils#runWithTrace} 统一处理：Quartz 路径复用
     * AbstractQuartzJob 放进 MDC 的值（同一个值会被写进 sys_job_log.job_message），
     * 非 Quartz 路径自行兜底。</p>
     */
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

    /**
     * 前台调用目标填写 accountQuartzTask.compensatePhoneSignSync() 时执行。
     *
     * <p>触发 account-server 扫 USER_PHONE_CHANGE_LOG 里 SIGN_SYNC_STATUS 为 PENDING / FAILED
     * 的行，逐条向支付域重推显示账号。扫描范围与批量上限由下游决定，本任务不传参。</p>
     *
     * <p>失败 MUST 抛异常 —— sys_job_log 的成功/失败判定就看有没有异常抛出，
     * 只打日志会让「补偿一直没生效」在调度日志里显示为成功。</p>
     */
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
