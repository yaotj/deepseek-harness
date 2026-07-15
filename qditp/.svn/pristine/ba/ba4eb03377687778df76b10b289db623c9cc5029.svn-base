package com.chinasofti.huateng.micro.monitor.configuration;


import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({MonitorConfig.class, ExporterAutoConfiguration.class, ZipkinOptimizeConfig.class})
public @interface EnableExporterAutoConfig {
}
