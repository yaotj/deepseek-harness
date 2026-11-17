package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("gateTxnPayQuartzTask")
public class GateTxnPayQuartzTask
{
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayQuartzTask.class);

    private final GateTxnPayClient gateTxnPayClient;

    public GateTxnPayQuartzTask(GateTxnPayClient gateTxnPayClient)
    {
        this.gateTxnPayClient = gateTxnPayClient;
    }

    public void recoverOfflineFare()
    {
        QuartzTraceUtils.runWithTrace(this::recoverOfflineFareOnce);
    }

    public void pushMetroTransfer()
    {
        QuartzTraceUtils.runWithTrace(this::pushMetroTransferOnce);
    }

    public void retryDefaultChannelDebits()
    {
        QuartzTraceUtils.runWithTrace(this::retryDefaultChannelDebitsOnce);
    }

    public void retryAlipayChannelDebits()
    {
        QuartzTraceUtils.runWithTrace(this::retryAlipayChannelDebitsOnce);
    }

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

    private void consumeRetryQueueOnce(String traceId)
    {
        CommonResult response = gateTxnPayClient.consumeRetryQueue(QuartzTraceUtils.traceHeaders(traceId));
        check(response, "用户主动重试扣费队列消费");
    }

    private void processOneSideOrdersOnce(String traceId)
    {
        CommonResult response = gateTxnPayClient.processOneSideOrders(QuartzTraceUtils.traceHeaders(traceId));
        check(response, "单边订单处理");
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

    public void consumeRetryQueue()
    {
        QuartzTraceUtils.runWithTrace(this::consumeRetryQueueOnce);
    }

    public void processOneSideOrders()
    {
        QuartzTraceUtils.runWithTrace(this::processOneSideOrdersOnce);
    }
}
