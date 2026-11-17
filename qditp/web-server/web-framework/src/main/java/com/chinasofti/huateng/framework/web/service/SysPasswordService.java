package com.chinasofti.huateng.framework.web.service;

import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.chinasofti.huateng.common.constant.CacheConstants;
import com.chinasofti.huateng.common.core.domain.entity.SysUser;
import com.chinasofti.huateng.common.core.cache.LocalCache;
import com.chinasofti.huateng.common.exception.user.UserPasswordNotMatchException;
import com.chinasofti.huateng.common.exception.user.UserPasswordRetryLimitExceedException;
import com.chinasofti.huateng.common.utils.SecurityUtils;
import com.chinasofti.huateng.framework.security.context.AuthenticationContextHolder;


@Component
public class SysPasswordService
{
    @Autowired
    private LocalCache localCache;

    @Value(value = "${user.password.maxRetryCount}")
    private int maxRetryCount;

    @Value(value = "${user.password.lockTime}")
    private int lockTime;


    private String getCacheKey(String username)
    {
        return CacheConstants.PWD_ERR_CNT_KEY + username;
    }

    public void validate(SysUser user)
    {
        Authentication usernamePasswordAuthenticationToken = AuthenticationContextHolder.getContext();
        String username = usernamePasswordAuthenticationToken.getName();
        String password = usernamePasswordAuthenticationToken.getCredentials().toString();

        Integer retryCount = localCache.getCacheObject(getCacheKey(username));

        if (retryCount == null)
        {
            retryCount = 0;
        }

        if (retryCount >= Integer.valueOf(maxRetryCount).intValue())
        {
            throw new UserPasswordRetryLimitExceedException(maxRetryCount, lockTime);
        }

        if (!matches(user, password))
        {
            retryCount = retryCount + 1;
            localCache.setCacheObject(getCacheKey(username), retryCount, lockTime, TimeUnit.MINUTES);
            throw new UserPasswordNotMatchException();
        }
        else
        {
            clearLoginRecordCache(username);
        }
    }

    public boolean matches(SysUser user, String rawPassword)
    {
        return SecurityUtils.matchesPassword(rawPassword, user.getPassword());
    }

    public void clearLoginRecordCache(String loginName)
    {
        if (localCache.hasKey(getCacheKey(loginName)))
        {
            localCache.deleteObject(getCacheKey(loginName));
        }
    }
}


