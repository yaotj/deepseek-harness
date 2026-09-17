package com.chinasofti.huateng.quartz.service;

import java.util.List;
import com.chinasofti.huateng.quartz.domain.SysJobLog;

/**
 * 定时任务调度日志信息信息 服务层
 * 
 * @author zmzhang
 */
public interface ISysJobLogService
{
    /**
     * 获取quartz调度器日志的计划任务
     * 
     * @param jobLog 调度日志信息
     * @return 调度任务日志集合
     */
    public List<SysJobLog> selectJobLogList(SysJobLog jobLog);

    /**
     * 通过调度任务日志ID查询调度信息
     * 
     * @param jobLogId 调度任务日志ID
     * @return 调度任务日志对象信息
     */
    public SysJobLog selectJobLogById(Long jobLogId);

    /**
     * 新增任务日志
     * 
     * @param jobLog 调度日志信息
     */
    public void addJobLog(SysJobLog jobLog);

    /**
     * 按主键回写任务日志的执行结果
     *
     * @param jobLog 调度日志信息，MUST 带 jobLogId
     */
    public void updateJobLog(SysJobLog jobLog);

    /**
     * 把遗留的「进行中」记录批量收口
     *
     * @param runningStatus 需要被收口的状态值
     * @param targetStatus 收口后的状态值
     * @param exceptionInfo 收口原因
     * @return 被收口的行数
     */
    public int closeRunningJobLog(String runningStatus, String targetStatus, String exceptionInfo);

    /**
     * 批量删除调度日志信息
     * 
     * @param logIds 需要删除的日志ID
     * @return 结果
     */
    public int deleteJobLogByIds(Long[] logIds);

    /**
     * 删除任务日志
     * 
     * @param jobId 调度日志ID
     * @return 结果
     */
    public int deleteJobLogById(Long jobId);

    /**
     * 清空任务日志
     */
    public void cleanJobLog();
}
