package com.chinasofti.huateng.micro.web.utils;

import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;

public class FilterUtils {

    public static Logger log = LoggerFactory.getLogger(FilterUtils.class);

    public static void fastJsonResponse(ServletResponse servletResponse, int status, String data) {
        HttpServletResponse res = (HttpServletResponse) servletResponse;
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.setStatus(status);
        try {
            PrintWriter out = res.getWriter();
            out.println(data);
            out.flush();
            out.close();
        } catch (IOException e) {
            log.error("{}", e.getMessage(), e);
        }
    }
}
