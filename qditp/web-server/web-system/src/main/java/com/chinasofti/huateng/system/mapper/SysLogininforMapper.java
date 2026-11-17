package com.chinasofti.huateng.system.mapper;

import java.util.List;
import com.chinasofti.huateng.system.domain.SysLogininfor;


public interface SysLogininforMapper
{

    public void insertLogininfor(SysLogininfor logininfor);


    public List<SysLogininfor> selectLogininforList(SysLogininfor logininfor);


    public int deleteLogininforByIds(Long[] infoIds);


    public int cleanLogininfor();
}
