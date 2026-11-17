package com.chinasofti.huateng.system.service;

import java.util.List;
import com.chinasofti.huateng.system.domain.SysLogininfor;


public interface ISysLogininforService
{

    public void insertLogininfor(SysLogininfor logininfor);


    public List<SysLogininfor> selectLogininforList(SysLogininfor logininfor);


    public int deleteLogininforByIds(Long[] infoIds);


    public void cleanLogininfor();
}
