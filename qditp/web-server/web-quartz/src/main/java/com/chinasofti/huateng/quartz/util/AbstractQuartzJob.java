package com.chinasofti.huateng.quartz.util;

import java.util.Date;
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

    /** 本次执行在 sys_job_log 里那一行的主键。 */
    private static final ThreadLocal<Long> JOB_LOG_ID = new ThreadLocal<>();

    /** MDC 中链路追踪标识的键名，与 {@link QuartzTraceUtils#TRACE_ID_KEY} 同源。 */
    private static final String TRACE_ID_KEY = QuartzTraceUtils.TRACE_ID_KEY;

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
        JOB_LOG_ID.remove();
        MDC.put(TRACE_ID_KEY, QuartzTraceUtils.newTraceId());
        insertRunningLog(sysJob);
    }

    /** 落一行「进行中」，让长任务在执行期间就能在前台被看到并按 traceId 检索。 */
    private void insertRunningLog(SysJob sysJob)
    {
        if (sysJob == null)
        {
            return;
        }
        try
        {
            SysJobLog runningLog = new SysJobLog();
            runningLog.setJobName(sysJob.getJobName());
            runningLog.setJobGroup(sysJob.getJobGroup());
            runningLog.setInvokeTarget(sysJob.getInvokeTarget());
            runningLog.setStatus(Constants.RUNNING);
            runningLog.setJobMessage(buildJobMessage(sysJob.getJobName() + " 执行中", MDC.get(TRACE_ID_KEY)));
            SpringUtils.getBean(ISysJobLogService.class).addJobLog(runningLog);
            JOB_LOG_ID.set(runningLog.getJobLogId());
        }
        catch (Exception e)
        {
            log.error("写入进行中调度日志失败，本次执行结果将在收口时补录 jobName={}", sysJob.getJobName(), e);
        }
    }

    /**
     * 拼接 job_message：带上 traceId，运维在前台「调度日志」看到后可直接去日志系统按该值检索本次执行的全链路日志。
     *
     * <p>job_message 是 varchar(500)，原文本很短，追加 32 位 traceId 不会截断。</p>
     */
    private String buildJobMessage(String text, String traceId)
    {
        if (StringUtils.isNotEmpty(traceId))
        {
            return text + "，traceId=" + traceId;
        }
        return text;
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
        Long jobLogId = JOB_LOG_ID.get();
        JOB_LOG_ID.remove();
        String traceId = MDC.get(TRACE_ID_KEY);

        try
        {
            final SysJobLog sysJobLog = new SysJobLog();
            sysJobLog.setJobLogId(jobLogId);
            sysJobLog.setJobName(sysJob.getJobName());
            sysJobLog.setJobGroup(sysJob.getJobGroup());
            sysJobLog.setInvokeTarget(sysJob.getInvokeTarget());
            sysJobLog.setStartTime(startTime);
            sysJobLog.setStopTime(new Date());
            long runMs = sysJobLog.getStopTime().getTime() - sysJobLog.getStartTime().getTime();
            sysJobLog.setJobMessage(
                    buildJobMessage(sysJobLog.getJobName() + " 总共耗时：" + runMs + "毫秒", traceId));
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

            // 写入数据库当中：before() 已落「进行中」时按主键回写，否则退化成新增
            ISysJobLogService jobLogService = SpringUtils.getBean(ISysJobLogService.class);
            if (jobLogId != null)
            {
                jobLogService.updateJobLog(sysJobLog);
            }
            else
            {
                jobLogService.addJobLog(sysJobLog);
            }
        }
        catch (Exception ex)
        {
            log.error("回写调度日志失败 jobName={}, jobLogId={}", sysJob.getJobName(), jobLogId, ex);
        }
        finally
        {
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
