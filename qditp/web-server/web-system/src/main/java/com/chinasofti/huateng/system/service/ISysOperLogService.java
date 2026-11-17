package com.chinasofti.huateng.system.service;

import java.util.List;
import com.chinasofti.huateng.system.domain.SysOperLog;


public interface ISysOperLogService
{

    public void insertOperlog(SysOperLog operLog);


    public List<SysOperLog> selectOperLogList(SysOperLog operLog);


    public int deleteOperLogByIds(Long[] operIds);


    public SysOperLog selectOperLogById(Long operId);


    public void cleanOperLog();
}
