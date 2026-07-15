package com.chinasofti.huateng.micro.web.validation;

import cn.hutool.json.JSONUtil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.*;
import java.util.regex.Pattern;

public class AntiInjectionValidator implements ConstraintValidator<AntiInjection, Object> {

    // 危险模式映射：key为注入类型，value为正则表达式（忽略大小写）
    private static final Map<String, List<Pattern>> DANGEROUS_PATTERNS = new HashMap<>();

    static {
        // 1. SQL注入危险模式（常见关键字和攻击语法）
        DANGEROUS_PATTERNS.put("sql", Arrays.asList(
                Pattern.compile("\\b(drop|delete|truncate|alter|insert|update|exec)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(union\\s+all|union\\s+select)\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\b(or|and)\\s+\\d+\\s*=\\s*\\d+\\b", Pattern.CASE_INSENSITIVE),
                Pattern.compile("xp_cmdshell", Pattern.CASE_INSENSITIVE),
                Pattern.compile("\\s*--\\s*", Pattern.CASE_INSENSITIVE) // SQL注释符
        ));

        // 2. XSS攻击危险模式（HTML/JS标签和事件）
        DANGEROUS_PATTERNS.put("xss", Arrays.asList(
                Pattern.compile("<script.*?>.*?</script>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
                Pattern.compile("onclick\\s*=\\s*['\"].*?['\"]", Pattern.CASE_INSENSITIVE),
                Pattern.compile("onload\\s*=\\s*['\"].*?['\"]", Pattern.CASE_INSENSITIVE),
                Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
                Pattern.compile("<iframe.*?>.*?</iframe>", Pattern.DOTALL | Pattern.CASE_INSENSITIVE),
                Pattern.compile("<(?!\\s*[a-zA-Z/])", Pattern.CASE_INSENSITIVE)
        ));

        // 3. 命令注入危险模式（系统命令分隔符和危险操作）
        DANGEROUS_PATTERNS.put("command", Arrays.asList(
                Pattern.compile(";\\s*[a-z0-9]+", Pattern.CASE_INSENSITIVE), // 命令分隔符;
                Pattern.compile("\\|\\s*[a-z0-9]+", Pattern.CASE_INSENSITIVE), // 管道符|
                Pattern.compile("rm\\s+-rf", Pattern.CASE_INSENSITIVE), // 删除命令
                Pattern.compile("bash\\s+|sh\\s+", Pattern.CASE_INSENSITIVE) // 执行shell
        ));
    }

    // 需要校验的注入类型（从注解参数获取）
    private Set<String> targetTypes;

    @Override
    public void initialize(AntiInjection constraintAnnotation) {
        // 初始化需要校验的类型（去重）
        targetTypes = new HashSet<>(Arrays.asList(constraintAnnotation.types()));
    }

    /**
     * 核心校验逻辑：检查对象中的所有字符串字段是否包含危险模式
     *
     * @param value 被校验的对象（请求体实体类）或字符串参数
     */
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // 允许null，由@NotNull单独控制
        }
        try {
            String valueStr = JSONUtil.parse(value).toString();
            return isSafe(valueStr);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查单个字符串是否包含危险模式
     */
    private boolean isSafe(String content) {
        for (String type : targetTypes) {
            List<Pattern> patterns = DANGEROUS_PATTERNS.get(type);
            if (patterns == null) continue;

            for (Pattern pattern : patterns) {
                if (pattern.matcher(content).find()) {
                    return false; // 匹配到危险模式
                }
            }
        }
        return true;
    }
}
