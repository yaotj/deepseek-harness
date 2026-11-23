package com.chinasofti.huateng.framework.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.filter.CorsFilter;
import com.chinasofti.huateng.framework.config.properties.PermitAllUrlProperties;
import com.chinasofti.huateng.framework.security.filter.JwtAuthenticationTokenFilter;
import com.chinasofti.huateng.framework.security.handle.AuthenticationEntryPointImpl;
import com.chinasofti.huateng.framework.security.handle.LogoutSuccessHandlerImpl;


@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
@Configuration
public class SecurityConfig
{

    @Autowired
    private UserDetailsService userDetailsService;
    

    @Autowired
    private AuthenticationEntryPointImpl unauthorizedHandler;


    @Autowired
    private LogoutSuccessHandlerImpl logoutSuccessHandler;


    @Autowired
    private JwtAuthenticationTokenFilter authenticationTokenFilter;
    

    @Autowired
    private CorsFilter corsFilter;


    @Autowired
    private PermitAllUrlProperties permitAllUrl;


    @Bean
    public AuthenticationManager authenticationManager()
    {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();
        daoAuthenticationProvider.setUserDetailsService(userDetailsService);
        daoAuthenticationProvider.setPasswordEncoder(bCryptPasswordEncoder());
        return new ProviderManager(daoAuthenticationProvider);
    }


    @Bean
    protected SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception
    {
        return httpSecurity
            .csrf(csrf -> csrf.disable())
            .headers((headersCustomizer) -> {
                headersCustomizer.cacheControl(cache -> cache.disable()).frameOptions(options -> options.sameOrigin());
            })
            .exceptionHandling(exception -> exception.authenticationEntryPoint(unauthorizedHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests((requests) -> {
                permitAllUrl.getUrls().forEach(url -> requests.requestMatchers(antMatcher(url)).permitAll());
                requests.requestMatchers(antMatchers("/login", "/register", "/captchaImage")).permitAll()
                    .requestMatchers(antMatchers("/", "/*.html", "/**/*.html", "/**/*.css", "/**/*.js", "/profile/**")).permitAll()
                    .requestMatchers(antMatchers("/swagger-ui/**", "/v3/api-docs/**")).permitAll()
                    .anyRequest().authenticated();
            })
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessHandler(logoutSuccessHandler))
            .addFilterBefore(authenticationTokenFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(corsFilter, JwtAuthenticationTokenFilter.class)
            .addFilterBefore(corsFilter, LogoutFilter.class)
            .build();
    }


    @Bean
    public BCryptPasswordEncoder bCryptPasswordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    private RequestMatcher antMatcher(String pattern)
    {
        return new AntPathRequestMatcher(pattern);
    }

    private RequestMatcher[] antMatchers(String... patterns)
    {
        RequestMatcher[] matchers = new RequestMatcher[patterns.length];
        for (int i = 0; i < patterns.length; i++)
        {
            matchers[i] = antMatcher(patterns[i]);
        }
        return matchers;
    }
}
