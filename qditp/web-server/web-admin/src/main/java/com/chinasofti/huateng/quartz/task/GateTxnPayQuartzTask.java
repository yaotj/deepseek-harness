package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** gate-txn-pay-server 的 Quartz 调用桥接任务：离线码金额补偿 / 公交换乘推送 / 行程扣费重试两条 / 补站扣费周期查询更新。 */
@Component("gateTxnPayQuartzTask")
public class GateTxnPayQuartzTask
{
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayQuartzTask.class);

    private final GateTxnPayClient gateTxnPayClient;

    public GateTxnPayQuartzTask(GateTxnPayClient gateTxnPayClient)
    {
        this.gateTxnPayClient = gateTxnPayClient;
    }

    /** 前台调用目标填写 gateTxnPayQuartzTask.recoverOfflineFare() 时执行。 */
    public void recoverOfflineFare()
    {
        QuartzTraceUtils.runWithTrace(this::recoverOfflineFareOnce);
    }

    /** 前台调用目标填写 gateTxnPayQuartzTask.pushMetroTransfer() 时执行。 */
    public void pushMetroTransfer()
    {
        QuartzTraceUtils.runWithTrace(this::pushMetroTransferOnce);
    }

    /** 前台调用目标填写 gateTxnPayQuartzTask.retryDefaultChannelDebits() 时执行（sys_job 220，非支付宝渠道）。 */
    public void retryDefaultChannelDebits()
    {
        QuartzTraceUtils.runWithTrace(this::retryDefaultChannelDebitsOnce);
    }

    /** 前台调用目标填写 gateTxnPayQuartzTask.retryAlipayChannelDebits() 时执行（sys_job 255，支付宝出行渠道）。 */
    public void retryAlipayChannelDebits()
    {
        QuartzTraceUtils.runWithTrace(this::retryAlipayChannelDebitsOnce);
    }

    /** 前台调用目标填写 gateTxnPayQuartzTask.retryRecentUnpaidDebits() 时执行（sys_job 345，近 N 分钟未扣费、每分钟一轮；2026-09-21 编号先后为 135、265、235、345）。 */
    public void retryRecentUnpaidDebits()
    {
        QuartzTraceUtils.runWithTrace(this::retryRecentUnpaidDebitsOnce);
    }

    private void recoverOfflineFareOnce(String traceId)
    {
        CommonResult response = gateTxnPayClient.recoverOfflineFare(QuartzTraceUtils.traceHeaders(traceId));
        check(response, "离线码金额补偿");
    }

    private void pushMetroTransferOnce(String traceId)
    {
        CommonResult response = gateTxnPayClient.pushMetroTransfer(QuartzTraceUtils.traceHeaders(traceId));
        check(response, "公交换乘推送");
    }

    private void retryDefaultChannelDebitsOnce(String traceId)
    {
        CommonResult response = gateTxnPayClient.retryDefaultChannelDebits(QuartzTraceUtils.traceHeaders(traceId));
        check(response, "行程扣费重试(非支付宝)");
    }

    private void retryAlipayChannelDebitsOnce(String traceId)
    {
        CommonResult response = gateTxnPayClient.retryAlipayChannelDebits(QuartzTraceUtils.traceHeaders(traceId));
        check(response, "支付宝出行重试扣费");
    }

    private void retryRecentUnpaidDebitsOnce(String traceId)
    {
        CommonResult response = gateTxnPayClient.retryRecentUnpaidDebits(QuartzTraceUtils.traceHeaders(traceId));
        check(response, "补站扣费周期查询更新");
    }

    private void check(CommonResult response, String action)
    {
        if (response == null)
        {
            throw new IllegalStateException("gate-txn-pay-server " + action + "接口未返回响应");
        }
        if (!"0000".equals(response.getRetCode()))
        {
            throw new IllegalStateException("gate-txn-pay-server " + action + "失败: retCode="
                    + response.getRetCode() + ", retMsg=" + response.getRetMsg());
        }
        log.info("gate-txn-pay-server {}调用成功, retMsg={}", action, response.getRetMsg());
    }
}
