package com.chinasofti.huateng.micro.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {SqlInjectValidator.class})
public @interface Sqlinject {

    String message() default "detecting sqlinject";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
