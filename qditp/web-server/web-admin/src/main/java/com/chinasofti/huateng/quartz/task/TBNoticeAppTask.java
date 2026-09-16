package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.f2f.F2FClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Function;

@Component("tbNoticeAppTask")
public class TBNoticeAppTask {


    private static final Logger log = LoggerFactory.getLogger(TBNoticeAppTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final F2FClient f2FClient;

    public TBNoticeAppTask(F2FClient f2FClient)
    {
        this.f2FClient = f2FClient;
    }

    /**
     * 测试
     * 前台调用目标填写 tbNoticeAppTask.testtbNoticeAppTask() 时执行。
     */
    public void testtbNoticeAppTask()
    {
        invoke("测试", f2FClient::testtbNoticeAppTask);
    }

    /**
     * 扫码取票接口 通知app出票结果
     * 前台调用目标填写 tbNoticeAppTask.noticeTakeTicketTask() 时执行。
     */
    public void noticeTakeTicketTask()
    {
        invoke("扫码取票接口 通知app出票结果", f2FClient::noticeTakeTicketTask);
    }

    /**
     * 扫码取票接口 通知app出票故障结果
     * 前台调用目标填写 tbNoticeAppTask.noticeTakeTicketFailureTask() 时执行。
     */
    public void noticeTakeTicketFailureTask()
    {
        invoke("扫码取票接口 通知app出票故障结果", f2FClient::noticeTakeTicketFailureTask);
    }

    /**
     * 扫码取票接口 通知app退款结果
     * 前台调用目标填写 tbNoticeAppTask.noticeRefundTask() 时执行。
     */
    public void noticeRefundTask()
    {
        invoke("扫码取票接口 通知app退款结果", f2FClient::noticeRefundTask);
    }

    /**
     * 四个入口的结果判定完全一致，统一在此做：失败 MUST 抛异常，
     * Quartz 只以异常判定失败，静默返回会让调度日志记成成功。
     *
     * <p>traceId 由 {@link QuartzTraceUtils#runWithTrace} 统一处理：Quartz 路径复用
     * AbstractQuartzJob 放进 MDC 的值（同一个值会被写进 sys_job_log.job_message），
     * MUST NOT 另生成一个，否则前台调度日志里的 traceId 与实际发给 collect-pay 的对不上。</p>
     */
    private void invoke(String bizName, Function<Map<String, String>, ? extends CommonResult> action)
    {
        QuartzTraceUtils.runWithTrace(traceId -> {
            CommonResult response = action.apply(QuartzTraceUtils.traceHeaders(traceId));
            if (response == null)
            {
                throw new IllegalStateException("collect-pay-server Quartz 联调接口未返回响应: " + bizName);
            }
            if (!SUCCESS_CODE.equals(response.getRetCode()))
            {
                throw new IllegalStateException("collect-pay-server Quartz 联调失败: " + bizName
                        + ", retCode=" + response.getRetCode() + ", retMsg=" + response.getRetMsg());
            }
            log.info("调用 collect-pay-server Quartz {} 联调调用成功, retMsg={}", bizName, response.getRetMsg());
        });
    }


}

