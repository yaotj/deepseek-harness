package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.para.ParaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** para-server 的 Quartz 调用桥接任务。 */
@Component("paraQuartzTask")
public class ParaQuartzTask
{
    private static final Logger log = LoggerFactory.getLogger(ParaQuartzTask.class);

    private static final String SUCCESS_CODE = "0000";

    private final ParaClient paraClient;

    public ParaQuartzTask(ParaClient paraClient)
    {
        this.paraClient = paraClient;
    }

    /** 前台调用目标填写 paraQuartzTask.scanFtpPara() 时执行。 */
    public void scanFtpPara()
    {
        QuartzTraceUtils.runWithTrace(this::invokeOnce);
    }

    /** ⚠️ para-server 侧要真正把 traceparent 续成 MDC 的 {@code traceId}，需要该模块 */
    private void invokeOnce(String traceId)
    {
        CommonResult response = paraClient.quartzScanFtpPara(QuartzTraceUtils.traceHeaders(traceId));
        if (response == null)
        {
            throw new IllegalStateException("para-server FTP参数扫描接口未返回响应");
        }
        if (!SUCCESS_CODE.equals(response.getRetCode()))
        {
            throw new IllegalStateException("para-server FTP参数扫描失败: retCode=" + response.getRetCode()
                    + ", retMsg=" + response.getRetMsg());
        }
        log.info("para-server FTP参数扫描调用成功, retMsg={}", response.getRetMsg());
    }
}
