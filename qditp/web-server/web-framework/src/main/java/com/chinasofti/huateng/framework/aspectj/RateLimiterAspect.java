package com.chinasofti.huateng.framework.aspectj;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.chinasofti.huateng.common.annotation.RateLimiter;
import com.chinasofti.huateng.common.core.cache.LocalCache;
import com.chinasofti.huateng.common.enums.LimitType;
import com.chinasofti.huateng.common.exception.ServiceException;
import com.chinasofti.huateng.common.utils.StringUtils;
import com.chinasofti.huateng.common.utils.ip.IpUtils;

/**
 * 限流处理
 *
 * @author zmzhang
 */
@Aspect
@Component
public class RateLimiterAspect
{
    private static final Logger log = LoggerFactory.getLogger(RateLimiterAspect.class);

    @Autowired
    private LocalCache localCache;

    private static class LimitCounter
    {
        private int count;

        private long expireAt;
    }

    @Before("@annotation(rateLimiter)")
    public void doBefore(JoinPoint point, RateLimiter rateLimiter) throws Throwable
    {
        int time = rateLimiter.time();
        int count = rateLimiter.count();

        String combineKey = getCombineKey(rateLimiter, point);
        try
        {
            int number = increment(combineKey, time);
            if (number > count)
            {
                throw new ServiceException("访问过于频繁，请稍候再试");
            }
            log.info("限制请求'{}',当前请求'{}',缓存key'{}'", count, number, combineKey);
        }
        catch (ServiceException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException("服务器限流异常，请稍候再试");
        }
    }

    private int increment(String key, int seconds)
    {
        synchronized (key.intern())
        {
            long now = System.currentTimeMillis();
            LimitCounter counter = localCache.getCacheObject(key);
            if (StringUtils.isNull(counter) || counter.expireAt <= now)
            {
                counter = new LimitCounter();
                counter.expireAt = now + TimeUnit.SECONDS.toMillis(seconds);
            }
            counter.count++;
            localCache.setCacheObject(key, counter, seconds, TimeUnit.SECONDS);
            return counter.count;
        }
    }

    public String getCombineKey(RateLimiter rateLimiter, JoinPoint point)
    {
        StringBuffer stringBuffer = new StringBuffer(rateLimiter.key());
        if (rateLimiter.limitType() == LimitType.IP)
        {
            stringBuffer.append(IpUtils.getIpAddr()).append("-");
        }
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = method.getDeclaringClass();
        stringBuffer.append(targetClass.getName()).append("-").append(method.getName());
        return stringBuffer.toString();
    }
}
