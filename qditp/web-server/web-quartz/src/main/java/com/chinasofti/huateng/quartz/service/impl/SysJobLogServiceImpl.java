package com.chinasofti.huateng.quartz.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.chinasofti.huateng.quartz.domain.SysJobLog;
import com.chinasofti.huateng.quartz.mapper.SysJobLogMapper;
import com.chinasofti.huateng.quartz.service.ISysJobLogService;

/**
 * 定时任务调度日志信息 服务层
 * 
 * @author zmzhang
 */
@Service
public class SysJobLogServiceImpl implements ISysJobLogService
{
    @Autowired
    private SysJobLogMapper jobLogMapper;

    /**
     * 获取quartz调度器日志的计划任务
     * 
     * @param jobLog 调度日志信息
     * @return 调度任务日志集合
     */
    @Override
    public List<SysJobLog> selectJobLogList(SysJobLog jobLog)
    {
        return jobLogMapper.selectJobLogList(jobLog);
    }

    /**
     * 通过调度任务日志ID查询调度信息
     * 
     * @param jobLogId 调度任务日志ID
     * @return 调度任务日志对象信息
     */
    @Override
    public SysJobLog selectJobLogById(Long jobLogId)
    {
        return jobLogMapper.selectJobLogById(jobLogId);
    }

    /**
     * 新增任务日志
     * 
     * @param jobLog 调度日志信息
     */
    @Override
    public void addJobLog(SysJobLog jobLog)
    {
        jobLogMapper.insertJobLog(jobLog);
    }

    /**
     * 按主键回写任务日志的执行结果
     *
     * @param jobLog 调度日志信息，MUST 带 jobLogId
     */
    @Override
    public void updateJobLog(SysJobLog jobLog)
    {
        jobLogMapper.updateJobLog(jobLog);
    }

    /**
     * 把遗留的「进行中」记录批量收口
     *
     * @param runningStatus 需要被收口的状态值
     * @param targetStatus 收口后的状态值
     * @param exceptionInfo 收口原因
     * @return 被收口的行数
     */
    @Override
    public int closeRunningJobLog(String runningStatus, String targetStatus, String exceptionInfo)
    {
        return jobLogMapper.closeRunningJobLog(runningStatus, targetStatus, exceptionInfo);
    }

    /**
     * 批量删除调度日志信息
     * 
     * @param logIds 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteJobLogByIds(Long[] logIds)
    {
        return jobLogMapper.deleteJobLogByIds(logIds);
    }

    /**
     * 删除任务日志
     * 
     * @param jobId 调度日志ID
     */
    @Override
    public int deleteJobLogById(Long jobId)
    {
        return jobLogMapper.deleteJobLogById(jobId);
    }

    /**
     * 清空任务日志
     */
    @Override
    public void cleanJobLog()
    {
        jobLogMapper.cleanJobLog();
    }
}
