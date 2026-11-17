package com.chinasofti.huateng.common.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


public class RefererFilter implements Filter
{

    public List<String> allowedDomains;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException
    {
        String domains = filterConfig.getInitParameter("allowedDomains");
        this.allowedDomains = Arrays.asList(domains.split(","));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException
    {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String referer = req.getHeader("Referer");

        if (referer == null || referer.isEmpty())
        {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Referer header is required");
            return;
        }

        boolean allowed = false;
        for (String domain : allowedDomains)
        {
            if (referer.contains(domain))
            {
                allowed = true;
                break;
            }
        }

        if (allowed)
        {
            chain.doFilter(request, response);
        }
        else
        {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Access denied: Referer '" + referer + "' is not allowed");
        }
    }

    @Override
    public void destroy()
    {

    }
}
