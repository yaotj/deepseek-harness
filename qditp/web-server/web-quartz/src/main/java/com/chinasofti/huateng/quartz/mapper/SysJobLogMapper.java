package com.chinasofti.huateng.quartz.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.chinasofti.huateng.quartz.domain.SysJobLog;

/**
 * 调度任务日志信息 数据层
 * 
 * @author zmzhang
 */
public interface SysJobLogMapper
{
    /**
     * 获取quartz调度器日志的计划任务
     * 
     * @param jobLog 调度日志信息
     * @return 调度任务日志集合
     */
    public List<SysJobLog> selectJobLogList(SysJobLog jobLog);

    /**
     * 查询所有调度任务日志
     *
     * @return 调度任务日志列表
     */
    public List<SysJobLog> selectJobLogAll();

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
     * @return 结果
     */
    public int insertJobLog(SysJobLog jobLog);

    /**
     * 按主键回写任务日志的执行结果（状态 / 描述 / 异常）
     *
     * <p>配合 {@link #insertJobLog} 在任务开始时先落「进行中」使用，
     * 因此 create_time 保持插入时刻不动，即任务真实开始时间。</p>
     *
     * @param jobLog 调度日志信息，MUST 带 jobLogId
     * @return 结果
     */
    public int updateJobLog(SysJobLog jobLog);

    /**
     * 把遗留的「进行中」记录批量收口为指定状态
     *
     * <p>Quartz 是内存 JobStore，web-admin 进程重启后上一轮执行的结果永远不会回写，
     * 那些行会一直悬在「进行中」。启动时扫一次即可，本方法 NEVER 用于运行期。</p>
     *
     * @param runningStatus 需要被收口的状态值
     * @param targetStatus 收口后的状态值
     * @param exceptionInfo 收口原因
     * @return 结果
     */
    public int closeRunningJobLog(@Param("runningStatus") String runningStatus,
            @Param("targetStatus") String targetStatus, @Param("exceptionInfo") String exceptionInfo);


    /**
     * 批量删除调度日志信息
     * 
     * @param logIds 需要删除的数据ID
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
