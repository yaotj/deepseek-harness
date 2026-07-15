package com.chinasofti.huateng.micro.web;

import com.chinasofti.huateng.log4j2.CheckLoggerHealth;
import com.chinasofti.huateng.log4j2.CustomLoggingConfiguration;
import com.chinasofti.huateng.micro.monitor.configuration.EnableExporterAutoConfig;
import com.chinasofti.huateng.micro.web.utils.HostManager;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.*;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableAspectJAutoProxy
@ComponentScan({"com.chinasofti.huateng.micro.controller", "com.chinasofti.huateng.micro.web", "com.chinasofti.huateng.micro.monitor.trace"})
@PropertySource("classpath:web.properties")
@EnableAsync
@EnableScheduling
@EnableExporterAutoConfig
@AutoConfigureBefore(name = "com.github.xiaoymin.knife4j.spring.configuration.Knife4jAutoConfiguration")
public class WebAutoConfig {

    @Bean(name = "hostManager")
    public HostManager hostManager() {
        return new HostManager();
    }

    @Bean
    public WebMvcConfigurer webConfigurer() {
        return new DefaultMvcConfigurer();
    }

    @Bean
    public CustomLoggingConfiguration customLoggingConfigurer() {
        return new CustomLoggingConfiguration();
    }

    @Bean
    public CheckLoggerHealth checkLoggerHealth() {
        return new CheckLoggerHealth();
    }

}
