package com.chinasofti.huateng.micro.web.filter;

import com.chinasofti.huateng.micro.web.Webconfig;
import com.chinasofti.huateng.micro.web.utils.FilterUtils;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Order(Ordered.HIGHEST_PRECEDENCE + 3)
@Component
public class ActuatorFilter implements Filter {

    public static Logger log = LoggerFactory.getLogger(ActuatorFilter.class);

    @Autowired
    Webconfig webconfig;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            if (!checkIp(request)) {
                FilterUtils.fastJsonResponse(servletResponse, 403, "{\"msg\":\"can not request\"}");
                return;
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    private boolean checkIp(HttpServletRequest httpServletRequest) {
        List<String> ips = webconfig.getActuatorAllowIpList();
        if (ips == null) {
            return true;
        } else {
            String ip = httpServletRequest.getRemoteAddr();
            if (ip.startsWith("127.") || ip.equals("0:0:0:0:0:0:0:1")) {
                return true;
            }
            if (ips.contains(ip)) {
                return true;
            } else {
                return false;
            }
        }
    }


}
