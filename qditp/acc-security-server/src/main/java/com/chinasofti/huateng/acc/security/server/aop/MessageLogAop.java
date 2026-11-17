package com.chinasofti.huateng.acc.security.server.aop;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.common.response.ResultMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;



@Aspect
@Component
public class MessageLogAop {

    private static Logger logger = LoggerFactory.getLogger(MessageLogAop.class);


    private static String getRemoteAddrIP(HttpServletRequest request) {
        String ip = null;
        String ipAddr = request.getHeader("X-Forwarded-For");
        if (ipAddr == null || ipAddr.isEmpty() || "unknown".equals(ipAddr)) {
            ipAddr = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddr == null || ipAddr.isEmpty() || "unknown".equals(ipAddr)) {
            ipAddr = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddr == null || ipAddr.isEmpty() || "unknown".equals(ipAddr)) {
            ipAddr = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ipAddr == null || ipAddr.isEmpty() || "unknown".equals(ipAddr)) {
            ipAddr = request.getHeader("X-Real-IP");
        }
        if (ipAddr != null && !ipAddr.isEmpty()) {
            ip = ipAddr.split(",")[0];
        }
        if (ipAddr == null || ipAddr.isEmpty() || "unknown".equals(ipAddr)) {
            ip = request.getRemoteAddr();
            ip = "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : request.getRemoteAddr();
        }
        return ip;
    }

    @Pointcut("execution(public * com.chinasofti.huateng.acc.security.server.controller.*.*(..))")
    public void messageLogAop() {
    }

    @Around("messageLogAop()")
    public Object arround(ProceedingJoinPoint pjp) {
        Object result = null;
        try {

            long beginTime = System.currentTimeMillis();
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            HttpServletRequest request = attributes.getRequest();

            String url = request.getRequestURL().toString();
            String inParams = "";
            if (pjp.getArgs().length != 0) {
                inParams = JSONObject.toJSONString(pjp.getArgs()[0]);
            }
            String remoteIp = getRemoteAddrIP(request);

            result = pjp.proceed();
            String outParams = JSON.toJSONString(result);
            long handleTime = (System.currentTimeMillis() - beginTime);
            logger.info("[AOP] 请求ip{}, 请求URL{}, 传入参数{}, 返回值{}, 处理时间{}",
                    remoteIp, url, inParams, outParams, handleTime);

        } catch (Throwable e) {
            logger.error("AOP失败{}, message log{}", e.toString());
            return ResultMapper.error();
        }
        return result;
    }
}
