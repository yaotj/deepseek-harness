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


    public void testtbNoticeAppTask()
    {
        invoke("测试", f2FClient::testtbNoticeAppTask);
    }


    public void noticeTakeTicketTask()
    {
        invoke("扫码取票接口 通知app出票结果", f2FClient::noticeTakeTicketTask);
    }


    public void noticeTakeTicketFailureTask()
    {
        invoke("扫码取票接口 通知app出票故障结果", f2FClient::noticeTakeTicketFailureTask);
    }


    public void noticeRefundTask()
    {
        invoke("扫码取票接口 通知app退款结果", f2FClient::noticeRefundTask);
    }

    private void invoke(String bizName, Function<Map<String, String>, ? extends CommonResult> action)
    {
        QuartzTraceUtils.runWithTrace(traceId -> {
            CommonResult response = action.apply(QuartzTraceUtils.traceHeaders(traceId));
            if (response == null)
            {
                throw new IllegalStateException("f2f-service(face-pay) Quartz 联调接口未返回响应: " + bizName);
            }
            if (!SUCCESS_CODE.equals(response.getRetCode()))
            {
                throw new IllegalStateException("f2f-service(face-pay) Quartz 联调失败: " + bizName
                        + ", retCode=" + response.getRetCode() + ", retMsg=" + response.getRetMsg());
            }
            log.info("调用 f2f-service(face-pay) Quartz {} 联调调用成功, retMsg={}", bizName, response.getRetMsg());
        });
    }

}

