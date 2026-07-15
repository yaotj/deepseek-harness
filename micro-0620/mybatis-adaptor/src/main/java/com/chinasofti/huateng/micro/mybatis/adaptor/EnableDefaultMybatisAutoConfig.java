package com.chinasofti.huateng.micro.mybatis.adaptor;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({DefaultMybatisConfiguration.class, DynamicMybatisConfiguration.class})
public @interface EnableDefaultMybatisAutoConfig {
}
