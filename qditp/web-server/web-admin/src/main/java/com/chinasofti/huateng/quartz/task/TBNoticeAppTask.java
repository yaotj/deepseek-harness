package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.rpc.f2f.F2FClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("tbNoticeAppTask")
public class TBNoticeAppTask {


    private static final Logger log = LoggerFactory.getLogger(TBNoticeAppTask.class);

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
        CommonResult response = f2FClient.testtbNoticeAppTask();
        if (response == null)
        {
            throw new IllegalStateException("collect-pay-server Quartz 联调接口未返回响应");
        }
        if (!"0000".equals(response.getRetCode()))
        {
            throw new IllegalStateException("collect-pay-server Quartz 联调失败: retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
        log.info("collect-pay-server Quartz 测试 联调调用成功, retMsg={}", response.getRetMsg());
    }

    /**
     * 扫码取票接口 通知app出票结果
     * 前台调用目标填写 tbNoticeAppTask.noticeTakeTicketTask() 时执行。
     */
    public void noticeTakeTicketTask()
    {
        CommonResult response = f2FClient.noticeTakeTicketTask();
        if (response == null)
        {
            throw new IllegalStateException("collect-pay-server Quartz 联调接口未返回响应");
        }
        if (!"0000".equals(response.getRetCode()))
        {
            throw new IllegalStateException("collect-pay-server Quartz 联调失败: retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
        log.info("调用 collect-pay-server Quartz 扫码取票接口 通知app出票结果 联调调用成功, retMsg={}", response.getRetMsg());
    }

    /**
     * 扫码取票接口 通知app出票故障结果
     * 前台调用目标填写 tbNoticeAppTask.noticeTakeTicketFailureTask() 时执行。
     */
    public void noticeTakeTicketFailureTask()
    {
        CommonResult response = f2FClient.noticeTakeTicketFailureTask();
        if (response == null)
        {
            throw new IllegalStateException("collect-pay-server Quartz 联调接口未返回响应");
        }
        if (!"0000".equals(response.getRetCode()))
        {
            throw new IllegalStateException("collect-pay-server Quartz 联调失败: retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
        log.info("调用 collect-pay-server Quartz 扫码取票接口 通知app出票故障结果 联调调用成功, retMsg={}", response.getRetMsg());
    }

    /**
     * 扫码取票接口 通知app退款结果
     * 前台调用目标填写 tbNoticeAppTask.noticeRefundTask() 时执行。
     */
    public void noticeRefundTask()
    {
        CommonResult response = f2FClient.noticeRefundTask();
        if (response == null)
        {
            throw new IllegalStateException("collect-pay-server Quartz 联调接口未返回响应");
        }
        if (!"0000".equals(response.getRetCode()))
        {
            throw new IllegalStateException("collect-pay-server Quartz 联调失败: retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
        log.info("调用 collect-pay-server Quartz 扫码取票接口 通知app退款结果 联调调用成功, retMsg={}", response.getRetMsg());
    }


}
