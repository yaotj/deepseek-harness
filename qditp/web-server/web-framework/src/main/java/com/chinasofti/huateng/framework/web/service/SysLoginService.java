package com.chinasofti.huateng.framework.web.service;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.chinasofti.huateng.common.constant.CacheConstants;
import com.chinasofti.huateng.common.constant.Constants;
import com.chinasofti.huateng.common.constant.UserConstants;
import com.chinasofti.huateng.common.core.domain.entity.SysUser;
import com.chinasofti.huateng.common.core.domain.model.LoginUser;
import com.chinasofti.huateng.common.core.cache.LocalCache;
import com.chinasofti.huateng.common.exception.ServiceException;
import com.chinasofti.huateng.common.exception.user.BlackListException;
import com.chinasofti.huateng.common.exception.user.CaptchaException;
import com.chinasofti.huateng.common.exception.user.CaptchaExpireException;
import com.chinasofti.huateng.common.exception.user.UserNotExistsException;
import com.chinasofti.huateng.common.exception.user.UserPasswordRetryLimitExceedException;
import com.chinasofti.huateng.common.exception.user.UserPasswordNotMatchException;
import com.chinasofti.huateng.common.utils.DateUtils;
import com.chinasofti.huateng.common.utils.MessageUtils;
import com.chinasofti.huateng.common.utils.StringUtils;
import com.chinasofti.huateng.common.utils.ip.IpUtils;
import com.chinasofti.huateng.framework.manager.AsyncManager;
import com.chinasofti.huateng.framework.manager.factory.AsyncFactory;
import com.chinasofti.huateng.framework.security.context.AuthenticationContextHolder;
import com.chinasofti.huateng.system.service.ISysConfigService;
import com.chinasofti.huateng.system.service.ISysUserService;
import com.chinasofti.huateng.framework.web.service.SysPasswordService;


@Component
public class SysLoginService
{
    @Autowired
    private TokenService tokenService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Autowired
    private LocalCache localCache;
    
    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private SysPasswordService passwordService;

    @Value("${user.password.expireDays}")
    private int expireDays;


    public String login(String username, String password, String code, String uuid)
    {
        validateCaptcha(username, code, uuid);
        loginPreCheck(username, password);
        Authentication authentication = null;
        try
        {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);
            AuthenticationContextHolder.setContext(authenticationToken);
            authentication = authenticationManager.authenticate(authenticationToken);
        }
        catch (Exception e)
        {
            if (e instanceof BadCredentialsException)
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
                throw new UserPasswordNotMatchException();
            }
            else
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, e.getMessage()));
                throw new ServiceException(e.getMessage());
            }
        }
        finally
        {
            AuthenticationContextHolder.clearContext();
        }
        // 序号2: 登录失败处理 - 登录成功后清除失败计数缓存
        passwordService.clearLoginRecordCache(username);
        // 序号1: 密码过期检查
        SysUser user = userService.selectUserByUserName(username);
        if (user != null && expireDays > 0 && user.getPwdUpdateDate() != null)
        {
            if (DateUtils.differentDaysByMillisecond(user.getPwdUpdateDate(), DateUtils.getNowDate()) > expireDays)
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, "密码已过期"));
                throw new UserPasswordNotMatchException();
            }
        }
        AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_SUCCESS, MessageUtils.message("user.login.success")));
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        recordLoginInfo(loginUser.getUserId());
        return tokenService.createToken(loginUser);
    }


    public void validateCaptcha(String username, String code, String uuid)
    {
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        if (captchaEnabled)
        {
            String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + StringUtils.nvl(uuid, "");
            String captcha = localCache.getCacheObject(verifyKey);
            if (captcha == null)
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire")));
                throw new CaptchaExpireException();
            }
            localCache.deleteObject(verifyKey);
            if (!code.equalsIgnoreCase(captcha))
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error")));
                throw new CaptchaException();
            }
        }
    }


    public void loginPreCheck(String username, String password)
    {
        if (StringUtils.isEmpty(username) || StringUtils.isEmpty(password))
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("not.null")));
            throw new UserNotExistsException();
        }
        if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
            throw new UserPasswordNotMatchException();
        }
        if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
            throw new UserPasswordNotMatchException();
        }
        String blackStr = configService.selectConfigByKey("sys.login.blackIPList");
        if (IpUtils.isMatchedIp(blackStr, IpUtils.getIpAddr()))
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("login.blocked")));
            throw new BlackListException();
        }
    }


    public void recordLoginInfo(Long userId)
    {
        userService.updateLoginInfo(userId, IpUtils.getIpAddr(), DateUtils.getNowDate());
    }
}



