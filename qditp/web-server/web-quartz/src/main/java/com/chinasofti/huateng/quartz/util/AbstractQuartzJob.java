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


public abstract class AbstractQuartzJob implements Job
{
    private static final Logger log = LoggerFactory.getLogger(AbstractQuartzJob.class);


    private static ThreadLocal<Date> threadLocal = new ThreadLocal<>();

    private static final ThreadLocal<Long> JOB_LOG_ID = new ThreadLocal<>();

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


    protected void before(JobExecutionContext context, SysJob sysJob)
    {
        threadLocal.set(new Date());
        JOB_LOG_ID.remove();
        MDC.put(TRACE_ID_KEY, QuartzTraceUtils.newTraceId());
        insertRunningLog(sysJob);
    }

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


    private String buildJobMessage(String text, String traceId)
    {
        if (StringUtils.isNotEmpty(traceId))
        {
            return text + "，traceId=" + traceId;
        }
        return text;
    }


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


    protected abstract void doExecute(JobExecutionContext context, SysJob sysJob) throws Exception;
}
