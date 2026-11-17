package com.chinasofti.huateng.quartz.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.chinasofti.huateng.quartz.domain.SysJobLog;
import com.chinasofti.huateng.quartz.mapper.SysJobLogMapper;
import com.chinasofti.huateng.quartz.service.ISysJobLogService;


@Service
public class SysJobLogServiceImpl implements ISysJobLogService
{
    @Autowired
    private SysJobLogMapper jobLogMapper;


    @Override
    public List<SysJobLog> selectJobLogList(SysJobLog jobLog)
    {
        return jobLogMapper.selectJobLogList(jobLog);
    }


    @Override
    public SysJobLog selectJobLogById(Long jobLogId)
    {
        return jobLogMapper.selectJobLogById(jobLogId);
    }


    @Override
    public void addJobLog(SysJobLog jobLog)
    {
        jobLogMapper.insertJobLog(jobLog);
    }


    @Override
    public void updateJobLog(SysJobLog jobLog)
    {
        jobLogMapper.updateJobLog(jobLog);
    }


    @Override
    public int closeRunningJobLog(String runningStatus, String targetStatus, String exceptionInfo)
    {
        return jobLogMapper.closeRunningJobLog(runningStatus, targetStatus, exceptionInfo);
    }


    @Override
    public int deleteJobLogByIds(Long[] logIds)
    {
        return jobLogMapper.deleteJobLogByIds(logIds);
    }


    @Override
    public int deleteJobLogById(Long jobId)
    {
        return jobLogMapper.deleteJobLogById(jobId);
    }


    @Override
    public void cleanJobLog()
    {
        jobLogMapper.cleanJobLog();
    }
}
