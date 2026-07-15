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

/**
 * Description:
 *
 * @author houkepan
 * @date 2018/9/2 16:53
 */

@Aspect
@Component
public class MessageLogAop {

    private static Logger logger = LoggerFactory.getLogger(MessageLogAop.class);

    /**
     * 获取远程ip
     *
     * @param request
     * @return
     */
    private static String getRemoteAddrIP(HttpServletRequest request) {
        String ip = null;
        // X-Forwarded-For: Squid 服务代理
        String ipAddr = request.getHeader("X-Forwarded-For");
        // Proxy-Client-IP: Apache 服务代理
        if (ipAddr == null || ipAddr.isEmpty() || "unknown".equals(ipAddr)) {
            ipAddr = request.getHeader("Proxy-Client-IP");
        }
        // WL-Proxy-Client-IP: WebLogic 服务代理
        if (ipAddr == null || ipAddr.isEmpty() || "unknown".equals(ipAddr)) {
            ipAddr = request.getHeader("WL-Proxy-Client-IP");
        }
        // HTTP_CLIENT_IP: 有些服务代理
        if (ipAddr == null || ipAddr.isEmpty() || "unknown".equals(ipAddr)) {
            ipAddr = request.getHeader("HTTP_CLIENT_IP");
        }
        // X-Real-IP: nginx 服务代理
        if (ipAddr == null || ipAddr.isEmpty() || "unknown".equals(ipAddr)) {
            ipAddr = request.getHeader("X-Real-IP");
        }
        //有些网络通过多层代理,会获取到多个IP,通常以(,)分割开来,并且第一个IP为客户端真是IP
        if (ipAddr != null && !ipAddr.isEmpty()) {
            ip = ipAddr.split(",")[0];
        }
        // 如果还获取不到,最后再通过request.getRemoteAddr()获取
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

            // 记录下请求内容
            String url = request.getRequestURL().toString();
            // 默认只传入一个参数
            String inParams = "";
            if (pjp.getArgs().length != 0) {
                inParams = JSONObject.toJSONString(pjp.getArgs()[0]);
            }
            String remoteIp = getRemoteAddrIP(request);

            // 请求处理
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
