package com.chinasofti.huateng.acc.security.feign.annotation;

import java.lang.annotation.*;


@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Order {


    int value() default 0;
}
