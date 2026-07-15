package com.chinasofti.huateng.micro.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @description: 请求参数代码注入风险拦截
 **/
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {AntiInjectionValidator.class})
public @interface AntiInjection {

    String message() default "请求参数存在代码注入风险";

    String[] types() default {"sql", "xss", "command"};

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}