package com.chinasofti.huateng.micro.datasource;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({MoreDruidDataSourceConfig.class})
public @interface EnableMoreDruidDataSourceConfigAutoConfig {
}
