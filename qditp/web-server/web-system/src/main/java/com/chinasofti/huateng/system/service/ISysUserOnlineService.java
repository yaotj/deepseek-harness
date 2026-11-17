package com.chinasofti.huateng.system.service;

import com.chinasofti.huateng.common.core.domain.model.LoginUser;
import com.chinasofti.huateng.system.domain.SysUserOnline;


public interface ISysUserOnlineService
{

    public SysUserOnline selectOnlineByIpaddr(String ipaddr, LoginUser user);


    public SysUserOnline selectOnlineByUserName(String userName, LoginUser user);


    public SysUserOnline selectOnlineByInfo(String ipaddr, String userName, LoginUser user);


    public SysUserOnline loginUserToUserOnline(LoginUser user);
}
