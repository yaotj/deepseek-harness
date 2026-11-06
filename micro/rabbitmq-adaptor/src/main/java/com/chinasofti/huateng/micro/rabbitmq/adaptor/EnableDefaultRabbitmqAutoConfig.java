package com.chinasofti.huateng.micro.rabbitmq.adaptor;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({DefaultRabbitmqConfiguration.class})
public @interface EnableDefaultRabbitmqAutoConfig {
}
