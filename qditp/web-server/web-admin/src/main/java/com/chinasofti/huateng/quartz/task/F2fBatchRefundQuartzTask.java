package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.facepay.FacePayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Function;
import java.util.Map;

/**
 * face-pay-server 的 Quartz 调用桥接任务：三类每日批量退款。
 *
 * <p>三个方法对应三条 {@code sys_job}，各自保留甲方给的执行时间；停用其中一条不影响另两条。
 * 下游端点自带 {@code AtomicBoolean} 限流，返 {@code 9998} 时**不当失败**（否则前台每轮都记红）。
 */
@Component("f2fBatchRefundQuartzTask")
public class F2fBatchRefundQuartzTask
{
    /** 上一轮仍在执行，属限流、不是失败。 */
    private static final String CODE_BUSY = "9998";

    private static final Logger log = LoggerFactory.getLogger(F2fBatchRefundQuartzTask.class);

    private final FacePayClient facePayClient;

    public F2fBatchRefundQuartzTask(FacePayClient facePayClient)
    {
        this.facePayClient = facePayClient;
    }

    /** 前台调用目标填写 f2fBatchRefundQuartzTask.refundSingleTicket() 时执行。 */
    public void refundSingleTicket()
    {
        QuartzTraceUtils.runWithTrace(traceId -> invoke("单程票退票",
                facePayClient::batchRefundSingleTicket, traceId));
    }

    /** 前台调用目标填写 f2fBatchRefundQuartzTask.refundTopup() 时执行。 */
    public void refundTopup()
    {
        QuartzTraceUtils.runWithTrace(traceId -> invoke("TVM充值退款",
                facePayClient::batchRefundTopup, traceId));
    }

    /** 前台调用目标填写 f2fBatchRefundQuartzTask.refundNoCash() 时执行。 */
    public void refundNoCash()
    {
        QuartzTraceUtils.runWithTrace(traceId -> invoke("非现金收款退款",
                facePayClient::batchRefundNoCash, traceId));
    }

    private void invoke(String taskName, Function<Map<String, String>, CommonResult> call, String traceId)
    {
        CommonResult response = call.apply(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null)
        {
            throw new IllegalStateException("face-pay-server " + taskName + "接口未返回响应");
        }
        if (CODE_BUSY.equals(response.getRetCode()))
        {
            log.warn("face-pay-server {}被限流，上一轮仍在执行, retMsg={}", taskName, response.getRetMsg());
            return;
        }
        if (!"0000".equals(response.getRetCode()))
        {
            throw new IllegalStateException("face-pay-server " + taskName + "失败: retCode="
                    + response.getRetCode() + ", retMsg=" + response.getRetMsg());
        }
        log.info("face-pay-server {}调用成功, retMsg={}", taskName, response.getRetMsg());
    }
}
