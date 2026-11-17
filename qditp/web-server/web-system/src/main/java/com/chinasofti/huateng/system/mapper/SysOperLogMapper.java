package com.chinasofti.huateng.system.mapper;

import java.util.List;
import com.chinasofti.huateng.system.domain.SysOperLog;


public interface SysOperLogMapper
{

    public void insertOperlog(SysOperLog operLog);


    public List<SysOperLog> selectOperLogList(SysOperLog operLog);


    public int deleteOperLogByIds(Long[] operIds);


    public SysOperLog selectOperLogById(Long operId);


    public void cleanOperLog();
}
