package com.chinasofti.huateng.micro.web;

import com.chinasofti.huateng.micro.web.global.MoreInterceptor;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

public class DefaultMvcConfigurer implements WebMvcConfigurer {
    public static Logger log = LoggerFactory.getLogger(DefaultMvcConfigurer.class);

    @PostConstruct
    private void init() {
        log.info("Loading default config of webmvc");
        log.warn("if need add config of WebMvcConfigurer,implements org.springframework.web.servlet.config.annotation.WebMvcConfigurer");
    }

    @Autowired
    MoreInterceptor moreInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowCredentials(true)
                .allowedOrigins("**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("**")
                .exposedHeaders("**");
    }

    public void addResourceHandlers(ResourceHandlerRegistry registry) {

    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(moreInterceptor).addPathPatterns("/**");
    }

}
