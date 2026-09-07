package com.chinasofti.huateng.quartz.util;

import java.util.Date;
import java.util.UUID;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import com.chinasofti.huateng.common.constant.Constants;
import com.chinasofti.huateng.common.constant.ScheduleConstants;
import com.chinasofti.huateng.common.utils.ExceptionUtil;
import com.chinasofti.huateng.common.utils.StringUtils;
import com.chinasofti.huateng.common.utils.bean.BeanUtils;
import com.chinasofti.huateng.common.utils.spring.SpringUtils;
import com.chinasofti.huateng.quartz.domain.SysJob;
import com.chinasofti.huateng.quartz.domain.SysJobLog;
import com.chinasofti.huateng.quartz.service.ISysJobLogService;

/**
 * 抽象quartz调用
 *
 * @author zmzhang
 */
public abstract class AbstractQuartzJob implements Job
{
    private static final Logger log = LoggerFactory.getLogger(AbstractQuartzJob.class);

    /**
     * 线程本地变量
     */
    private static ThreadLocal<Date> threadLocal = new ThreadLocal<>();

    /**
     * MDC 中链路追踪标识的键名。
     *
     * <p>MUST 用驼峰 traceId：这是 Micrometer Tracing correlation 的字段名，
     * 也是 pay-sign 日志 pattern 与 VictoriaLogs appender 取值用的键，
     * 写成 trace_id 会与全链路对不上。</p>
     */
    private static final String TRACE_ID_KEY = "traceId";


    @Override
    public void execute(JobExecutionContext context)
    {
        SysJob sysJob = new SysJob();
        BeanUtils.copyBeanProp(sysJob, context.getMergedJobDataMap().get(ScheduleConstants.TASK_PROPERTIES));
        try
        {
            before(context, sysJob);
            if (sysJob != null)
            {
                doExecute(context, sysJob);
            }
            after(context, sysJob, null);
        }
        catch (Exception e)
        {
            log.error("任务执行异常  - ：", e);
            after(context, sysJob, e);
        }
    }

    /**
     * 执行前
     *
     * @param context 工作执行上下文对象
     * @param sysJob 系统计划任务
     */
    protected void before(JobExecutionContext context, SysJob sysJob)
    {
        threadLocal.set(new Date());
        // 每次调度生成一个 traceId：任务方法可用 MDC.get("traceId") 取出往下游传（W3C traceparent），
        // after() 再把它写进 sys_job_log.job_message，前台「调度日志」即可拿到这个值去日志系统检索。
        // Quartz 线程是池化复用的，这里直接覆盖上一次的值，不依赖上一次是否清理干净。
        MDC.put(TRACE_ID_KEY, UUID.randomUUID().toString().replace("-", ""));
    }

    /**
     * 执行后
     *
     * @param context 工作执行上下文对象
     * @param sysJob 系统计划任务
     */
    protected void after(JobExecutionContext context, SysJob sysJob, Exception e)
    {
        Date startTime = threadLocal.get();
        threadLocal.remove();
        String traceId = MDC.get(TRACE_ID_KEY);

        try
        {
            final SysJobLog sysJobLog = new SysJobLog();
            sysJobLog.setJobName(sysJob.getJobName());
            sysJobLog.setJobGroup(sysJob.getJobGroup());
            sysJobLog.setInvokeTarget(sysJob.getInvokeTarget());
            sysJobLog.setStartTime(startTime);
            sysJobLog.setStopTime(new Date());
            long runMs = sysJobLog.getStopTime().getTime() - sysJobLog.getStartTime().getTime();
            // 带上 traceId，运维在前台「调度日志」看到后可直接去日志系统按该值检索本次执行的全链路日志。
            // job_message 是 varchar(500)，原文本很短，追加 32 位 traceId 不会截断。
            String jobMessage = sysJobLog.getJobName() + " 总共耗时：" + runMs + "毫秒";
            if (StringUtils.isNotEmpty(traceId))
            {
                jobMessage = jobMessage + "，traceId=" + traceId;
            }
            sysJobLog.setJobMessage(jobMessage);
            if (e != null)
            {
                sysJobLog.setStatus(Constants.FAIL);
                String errorMsg = StringUtils.substring(ExceptionUtil.getExceptionMessage(e), 0, 2000);
                sysJobLog.setExceptionInfo(errorMsg);
            }
            else
            {
                sysJobLog.setStatus(Constants.SUCCESS);
            }

            // 写入数据库当中
            SpringUtils.getBean(ISysJobLogService.class).addJobLog(sysJobLog);
        }
        finally
        {
            // Quartz 线程池化复用，MUST 在此清理，否则下一个任务在 before() 覆盖前会短暂带着上一次的 traceId。
            MDC.remove(TRACE_ID_KEY);
        }
    }

    /**
     * 执行方法，由子类重载
     *
     * @param context 工作执行上下文对象
     * @param sysJob 系统计划任务
     * @throws Exception 执行过程中的异常
     */
    protected abstract void doExecute(JobExecutionContext context, SysJob sysJob) throws Exception;
}
