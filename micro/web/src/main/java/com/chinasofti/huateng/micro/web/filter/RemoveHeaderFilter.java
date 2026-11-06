package com.chinasofti.huateng.micro.web.filter;

import com.chinasofti.huateng.micro.web.Webconfig;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@Component
public class RemoveHeaderFilter implements Filter {


    @Autowired
    Webconfig webconfig;

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequestWrapper requestWrapper = new HttpServletRequestWrapper((HttpServletRequest) servletRequest) {
            @Override
            public String getHeader(String name) {
                for (String removeHeader : webconfig.getRemoveHeadersList()) {
                    if (name.toLowerCase().equals(removeHeader)) {
                        return null;
                    }
                }
                return super.getHeader(name);
            }
        };
        filterChain.doFilter(requestWrapper, servletResponse);
    }

}
