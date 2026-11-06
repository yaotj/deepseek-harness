package com.chinasofti.huateng.micro.web.global;

import com.chinasofti.huateng.micro.web.Webconfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MoreInterceptor implements HandlerInterceptor {

    public static Logger log = LoggerFactory.getLogger(MoreInterceptor.class);

    private ThreadLocal<Long> startTimeThreadLocal = new ThreadLocal<>();

    public static AtomicLong runningCnt = new AtomicLong();

    public static AtomicLong timeoutCnt = new AtomicLong();

    @Autowired
    Webconfig webconfig;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (webconfig.isEnableLogTakeTimesInInterceptor()) {
            startTimeThreadLocal.set(System.currentTimeMillis());
        }
        runningCnt.incrementAndGet();
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) throws Exception {

    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) throws Exception {

        try {
            if (webconfig.isEnableLogTakeTimesInInterceptor()) {
                if (handler instanceof HandlerMethod) {
                    HandlerMethod method = (HandlerMethod) handler;
                    String className = method.getBeanType().getName();
                    String methodName = method.getMethod().getName();
                    Long startTime = startTimeThreadLocal.get();
                    Long endTime = System.currentTimeMillis();
                    if (startTime == null) {
                        startTime = endTime;
                    }
                    long time = endTime - startTime;
                    StringBuilder logs = new StringBuilder();
                    logs.append("taketimes:").append(time).append("ms ");
                    logs.append(logRequest(request));
                    logs.append(" ").append(className).append("::").append(methodName);
                    logs.append(" response.status=").append(response.getStatus());
                    logs.append(" response.ContentType=").append(response.getContentType());

                    boolean timeOut = time >= webconfig.getSlowRequestOfMillis();
                    if (timeOut) {
                        timeoutCnt.getAndIncrement();
                    }
                    if (timeOut || response.getStatus() > 300) {
                        log.error(logs.toString());
                    } else {
                        log.info(logs.toString());
                    }
                }
            }

        } finally {
            startTimeThreadLocal.remove();
            runningCnt.decrementAndGet();
        }

    }

    private String logRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String url = request.getRequestURI();
        String ip = request.getRemoteAddr();
        String realIp = request.getHeader("x-real-ip");
        return MessageFormat.format("receive request url={0} method={1} client={2} real-client={3} "
                , url, method, ip, realIp);
    }
}
