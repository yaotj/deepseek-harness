package com.chinasofti.huateng.quartz.task;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.quartz.util.QuartzTraceUtils;
import com.chinasofti.huateng.rpc.para.ParaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * para-server 的 Quartz 调用桥接任务。
 *
 * <p>任务必须位于 Quartz 调用白名单包 com.chinasofti.huateng.quartz.task 下，
 * Quartz 仅调用本 Bean；跨服务调用由 ParaClient 完成。</p>
 */
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

    /**
     * 前台调用目标填写 paraQuartzTask.scanFtpPara() 时执行。
     *
     * <p>扫描FTP目录中的路网拓扑(0001)、费率(0004)参数文件，
     * 按「版本号 + MD5」判断有无变更，有变更时由 para-server 下载并解析入库。</p>
     *
     * <p>traceId 由 {@link QuartzTraceUtils#runWithTrace} 统一处理：Quartz 路径复用
     * AbstractQuartzJob 放进 MDC 的值，非 Quartz 路径自行兜底。</p>
     */
    public void scanFtpPara()
    {
        QuartzTraceUtils.runWithTrace(this::invokeOnce);
    }

    /**
     * 只调一次下游，失败 MUST 抛异常：Quartz 只以异常判定失败，静默返回会让调度日志记成成功。
     *
     * <p>NEVER 在单次调度内循环重扫：para-server 的判据是「版本号 + MD5」，同一批文件在
     * 第一轮就已收敛，再扫一轮只会重复下载 500KB 却拿到全 skipped。要提高时效性 MUST 调 cron。</p>
     *
     * <p>⚠️ para-server 侧要真正把 traceparent 续成 MDC 的 {@code traceId}，需要该模块
     * {@code management.tracing.enabled=true}（默认值在
     * {@code resource/micro/web/src/main/resources/web.properties} 里是 false，目前只有
     * pay-sign-server 显式打开）。未打开时头仍会发出去，但对端 {@code %X{traceId}} 取不到，
     * 只能靠 {@code x-vlogs-capture} 做日志采集——**这一点部署后 MUST 用日志实测确认**。</p>
     */
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
