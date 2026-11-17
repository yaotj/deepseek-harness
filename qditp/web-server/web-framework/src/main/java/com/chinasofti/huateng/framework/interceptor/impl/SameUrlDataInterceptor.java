package com.chinasofti.huateng.framework.interceptor.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.annotation.RepeatSubmit;
import com.chinasofti.huateng.common.constant.CacheConstants;
import com.chinasofti.huateng.common.core.cache.LocalCache;
import com.chinasofti.huateng.common.filter.RepeatedlyRequestWrapper;
import com.chinasofti.huateng.common.utils.StringUtils;
import com.chinasofti.huateng.common.utils.http.HttpHelper;
import com.chinasofti.huateng.framework.interceptor.RepeatSubmitInterceptor;


@Component
public class SameUrlDataInterceptor extends RepeatSubmitInterceptor
{
    public final String REPEAT_PARAMS = "repeatParams";

    public final String REPEAT_TIME = "repeatTime";

    @Value("${token.header}")
    private String header;

    @Autowired
    private LocalCache localCache;

    @SuppressWarnings("unchecked")
    @Override
    public boolean isRepeatSubmit(HttpServletRequest request, RepeatSubmit annotation)
    {
        String nowParams = "";
        if (request instanceof RepeatedlyRequestWrapper)
        {
            RepeatedlyRequestWrapper repeatedlyRequest = (RepeatedlyRequestWrapper) request;
            nowParams = HttpHelper.getBodyString(repeatedlyRequest);
        }

        if (StringUtils.isEmpty(nowParams))
        {
            nowParams = JSON.toJSONString(request.getParameterMap());
        }
        Map<String, Object> nowDataMap = new HashMap<String, Object>();
        nowDataMap.put(REPEAT_PARAMS, nowParams);
        nowDataMap.put(REPEAT_TIME, System.currentTimeMillis());

        String url = request.getRequestURI();

        String submitKey = StringUtils.trimToEmpty(request.getHeader(header));

        String cacheRepeatKey = CacheConstants.REPEAT_SUBMIT_KEY + url + submitKey;

        Object sessionObj = localCache.getCacheObject(cacheRepeatKey);
        if (sessionObj != null)
        {
            Map<String, Object> sessionMap = (Map<String, Object>) sessionObj;
            if (sessionMap.containsKey(url))
            {
                Map<String, Object> preDataMap = (Map<String, Object>) sessionMap.get(url);
                if (compareParams(nowDataMap, preDataMap) && compareTime(nowDataMap, preDataMap, annotation.interval()))
                {
                    return true;
                }
            }
        }
        Map<String, Object> cacheMap = new HashMap<String, Object>();
        cacheMap.put(url, nowDataMap);
        localCache.setCacheObject(cacheRepeatKey, cacheMap, annotation.interval(), TimeUnit.MILLISECONDS);
        return false;
    }


    private boolean compareParams(Map<String, Object> nowMap, Map<String, Object> preMap)
    {
        String nowParams = (String) nowMap.get(REPEAT_PARAMS);
        String preParams = (String) preMap.get(REPEAT_PARAMS);
        return nowParams.equals(preParams);
    }


    private boolean compareTime(Map<String, Object> nowMap, Map<String, Object> preMap, int interval)
    {
        long time1 = (Long) nowMap.get(REPEAT_TIME);
        long time2 = (Long) preMap.get(REPEAT_TIME);
        if ((time1 - time2) < interval)
        {
            return true;
        }
        return false;
    }
}



