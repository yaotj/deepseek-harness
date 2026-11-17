package com.chinasofti.huateng.micro.web.validation;

import cn.hutool.json.JSONUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.*;
import java.util.regex.Pattern;

public class AntiInjectionValidator implements ConstraintValidator<AntiInjection, Object> {

    private static final Map<String, List<Pattern>> DANGEROUS_PATTERNS = new HashMap<>();

    static {
        DANGEROUS_PATTERNS.put("sql", Arrays.asList(
                Pattern.compile("\\b(drop|delete|truncate|alter|insert|update|exec)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(union\\s+all|union\\s+select)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(or|and)\\s+\\d+\\s*=\\s*\\d+\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("xp_cmdshell", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\s*--\\s*", Pattern.CASE_INSENSITIVE)
        ));

        DANGEROUS_PATTERNS.put("xss", Arrays.asList(
                Pattern.compile("<script.*?>.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
                Pattern.compile("onclick\\s*=\\s*['\"].*?['\"]", Pattern.CASE_INSENSITIVE),
                Pattern.compile("onload\\s*=\\s*['\"].*?['\"]", Pattern.CASE_INSENSITIVE),
                Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<iframe.*?>.*?</iframe>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
                Pattern.compile("<(?!\\s*[a-zA-Z/])", Pattern.CASE_INSENSITIVE)
        ));

        DANGEROUS_PATTERNS.put("command", Arrays.asList(
                Pattern.compile(";\\s*[a-z0-9]+", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\|\\s*[a-z0-9]+", Pattern.CASE_INSENSITIVE),
                Pattern.compile("rm\\s+-rf", Pattern.CASE_INSENSITIVE),
                Pattern.compile("bash\\s+|sh\\s+", Pattern.CASE_INSENSITIVE)
        ));
    }

    private Set<String> targetTypes;

    @Override
    public void initialize(AntiInjection constraintAnnotation) {
        targetTypes = new HashSet<>(Arrays.asList(constraintAnnotation.types()));
    }


    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        try {
            String valueStr = JSONUtil.parse(value).toString();
            return isSafe(valueStr);
        } catch (Exception e) {
            return false;
        }
    }


    private boolean isSafe(String content) {
        for (String type : targetTypes) {
            List<Pattern> patterns = DANGEROUS_PATTERNS.get(type);
            if (patterns == null) continue;

            for (Pattern pattern : patterns) {
                if (pattern.matcher(content).find()) {
                    return false;
                }
            }
        }
        return true;
    }
}
