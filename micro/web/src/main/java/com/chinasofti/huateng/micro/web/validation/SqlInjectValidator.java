package com.chinasofti.huateng.micro.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class SqlInjectValidator implements ConstraintValidator<Sqlinject, CharSequence> {

    private Pattern pattern;

    @Override
    public void initialize(Sqlinject constraintAnnotation) {
        pattern = Pattern.compile(
                "(and|exec|insert|select|drop|grant|alter|delete|update|count|chr|mid|master|truncate|char|declare|or|;|\\*|=|\\(|\\)|')"
                , Pattern.CASE_INSENSITIVE);
    }

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.length() == 0) {
            return true;
        }
        return !pattern.matcher(value).find();
    }
}
