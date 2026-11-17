package com.chinasofti.huateng.quartz.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.chinasofti.huateng.quartz.domain.SysJobLog;


public interface SysJobLogMapper
{

    public List<SysJobLog> selectJobLogList(SysJobLog jobLog);


    public List<SysJobLog> selectJobLogAll();


    public SysJobLog selectJobLogById(Long jobLogId);


    public int insertJobLog(SysJobLog jobLog);


    public int updateJobLog(SysJobLog jobLog);


    public int closeRunningJobLog(@Param("runningStatus") String runningStatus,
            @Param("targetStatus") String targetStatus, @Param("exceptionInfo") String exceptionInfo);


    public int deleteJobLogByIds(Long[] logIds);


    public int deleteJobLogById(Long jobId);


    public void cleanJobLog();
}
