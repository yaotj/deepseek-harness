package com.chinasofti.huateng.quartz.mapper;

import java.util.List;
import com.chinasofti.huateng.quartz.domain.SysJob;


public interface SysJobMapper
{

    public List<SysJob> selectJobList(SysJob job);


    public List<SysJob> selectJobAll();


    public SysJob selectJobById(Long jobId);


    public int deleteJobById(Long jobId);


    public int deleteJobByIds(Long[] ids);


    public int updateJob(SysJob job);


    public int insertJob(SysJob job);
}
