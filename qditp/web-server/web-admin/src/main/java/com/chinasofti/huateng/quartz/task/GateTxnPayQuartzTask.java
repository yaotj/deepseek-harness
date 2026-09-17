package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** gate-txn-pay-server 的 Quartz 调用桥接任务：离线码金额补偿 + 公交换乘推送。 */
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
