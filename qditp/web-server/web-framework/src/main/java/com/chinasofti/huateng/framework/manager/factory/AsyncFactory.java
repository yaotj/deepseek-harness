package com.chinasofti.huateng.framework.manager.factory;

import java.util.TimerTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.chinasofti.huateng.common.constant.Constants;
import com.chinasofti.huateng.common.utils.LogUtils;
import com.chinasofti.huateng.common.utils.ServletUtils;
import com.chinasofti.huateng.common.utils.StringUtils;
import com.chinasofti.huateng.common.utils.ip.AddressUtils;
import com.chinasofti.huateng.common.utils.ip.IpUtils;
import com.chinasofti.huateng.common.utils.spring.SpringUtils;
import com.chinasofti.huateng.system.domain.SysLogininfor;
import com.chinasofti.huateng.system.domain.SysOperLog;
import com.chinasofti.huateng.system.service.ISysLogininforService;
import com.chinasofti.huateng.system.service.ISysOperLogService;
import eu.bitwalker.useragentutils.UserAgent;


public class AsyncFactory
{
    private static final Logger sys_user_logger = LoggerFactory.getLogger("sys-user");


    public static TimerTask recordLogininfor(final String username, final String status, final String message,
            final Object... args)
    {
        final UserAgent userAgent = UserAgent.parseUserAgentString(ServletUtils.getRequest().getHeader("User-Agent"));
        final String ip = IpUtils.getIpAddr();
        return new TimerTask()
        {
            @Override
            public void run()
            {
                String address = AddressUtils.getRealAddressByIP(ip);
                StringBuilder s = new StringBuilder();
                s.append(LogUtils.getBlock(ip));
                s.append(address);
                s.append(LogUtils.getBlock(username));
                s.append(LogUtils.getBlock(status));
                s.append(LogUtils.getBlock(message));
                sys_user_logger.info(s.toString(), args);
                String os = userAgent.getOperatingSystem().getName();
                String browser = userAgent.getBrowser().getName();
                SysLogininfor logininfor = new SysLogininfor();
                logininfor.setUserName(username);
                logininfor.setIpaddr(ip);
                logininfor.setLoginLocation(address);
                logininfor.setBrowser(browser);
                logininfor.setOs(os);
                logininfor.setMsg(message);
                if (StringUtils.equalsAny(status, Constants.LOGIN_SUCCESS, Constants.LOGOUT, Constants.REGISTER))
                {
                    logininfor.setStatus(Constants.SUCCESS);
                }
                else if (Constants.LOGIN_FAIL.equals(status))
                {
                    logininfor.setStatus(Constants.FAIL);
                }
                SpringUtils.getBean(ISysLogininforService.class).insertLogininfor(logininfor);
            }
        };
    }


    public static TimerTask recordOper(final SysOperLog operLog)
    {
        return new TimerTask()
        {
            @Override
            public void run()
            {
                operLog.setOperLocation(AddressUtils.getRealAddressByIP(operLog.getOperIp()));
                SpringUtils.getBean(ISysOperLogService.class).insertOperlog(operLog);
            }
        };
    }
}
