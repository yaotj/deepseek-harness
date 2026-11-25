package com.chinasofti.huateng.framework.security.filter;

import java.io.IOException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.chinasofti.huateng.common.constant.HttpStatus;
import com.chinasofti.huateng.common.core.domain.AjaxResult;
import com.chinasofti.huateng.common.core.domain.model.LoginUser;
import com.chinasofti.huateng.common.utils.ServletUtils;
import com.chinasofti.huateng.common.utils.StringUtils;
import com.chinasofti.huateng.framework.web.service.TokenService;

@Component
public class JwtAuthenticationTokenFilter extends OncePerRequestFilter {
    @Autowired
    private TokenService tokenService;

    @Value("${token.maxInactiveInterval:0}")
    private int maxInactiveInterval;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException
    {
        LoginUser loginUser = tokenService.getLoginUser(request);
        if (StringUtils.isNotNull(loginUser) && StringUtils.isNull(SecurityUtils.getAuthentication()))
        {
            // 序号2: 会话超时检查
            if (maxInactiveInterval > 0 && loginUser.getLoginTime() != null)
            {
                long idleMinutes = (System.currentTimeMillis() - loginUser.getLoginTime()) / (1000L * 60);
                if (idleMinutes > maxInactiveInterval)
                {
                    tokenService.delLoginUser(loginUser.getToken());
                    ServletUtils.renderString(response, AjaxResult.error(HttpStatus.UNAUTHORIZED, "会话已超时，请重新登录").toString());
                    return;
                }
            }
            tokenService.verifyToken(loginUser);
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        }
        chain.doFilter(request, response);
    }
}
