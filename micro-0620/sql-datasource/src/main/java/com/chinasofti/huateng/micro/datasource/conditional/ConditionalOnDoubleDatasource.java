package com.chinasofti.huateng.micro.datasource.conditional;

import org.springframework.context.annotation.Conditional;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(GenericDoubleDatasourceCondition.class)
public @interface ConditionalOnDoubleDatasource {
    String value() default "database1";
}
