package com.chinasofti.huateng.quartz.service;

import java.util.List;
import com.chinasofti.huateng.quartz.domain.SysJobLog;


public interface ISysJobLogService
{

    public List<SysJobLog> selectJobLogList(SysJobLog jobLog);


    public SysJobLog selectJobLogById(Long jobLogId);


    public void addJobLog(SysJobLog jobLog);


    public void updateJobLog(SysJobLog jobLog);


    public int closeRunningJobLog(String runningStatus, String targetStatus, String exceptionInfo);


    public int deleteJobLogByIds(Long[] logIds);


    public int deleteJobLogById(Long jobId);


    public void cleanJobLog();
}
