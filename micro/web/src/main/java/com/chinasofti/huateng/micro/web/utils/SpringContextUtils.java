package com.chinasofti.huateng.micro.web.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component("SpringContextUtils")
public class SpringContextUtils implements ApplicationContextAware, EnvironmentAware {

    private static Environment environment;

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextUtils.applicationContext = applicationContext;
    }

    @Override
    public void setEnvironment(Environment environment) {
        SpringContextUtils.environment = environment;
    }

    public static Environment getEnvironment() {
        return environment;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
