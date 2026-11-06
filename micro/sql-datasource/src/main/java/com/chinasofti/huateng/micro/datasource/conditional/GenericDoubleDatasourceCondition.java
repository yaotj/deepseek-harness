package com.chinasofti.huateng.micro.datasource.conditional;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

public class GenericDoubleDatasourceCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = context.getEnvironment();

        // 1. 先判断全局开关：other.sql.double-datasource 是否为true
        String doubleDatasourceFlag = env.getProperty("other.sql.double-datasource");
        if (doubleDatasourceFlag == null || !"true".equalsIgnoreCase(doubleDatasourceFlag)) {
            return false;
        }

        // 2. 读取注解上配置的数据源名称（如database1、database2）
        Map<String, Object> annotationAttrs = metadata.getAnnotationAttributes(ConditionalOnDoubleDatasource.class.getName());
        if (annotationAttrs == null) {
            return false;
        }
        // 获取注解value值（默认是database1）
        String datasourceName = (String) annotationAttrs.get("value");
        if (datasourceName == null || datasourceName.trim().isEmpty()) {
            return false;
        }

        // 3. 拼接目标数据源的配置key，通用化判断核心配置
        String urlKey = String.format("spring.datasource.druid.%s.url", datasourceName);
        String usernameKey = String.format("spring.datasource.druid.%s.username", datasourceName);

        // 检查该数据源下的核心配置是否存在且有效
        String dbUrl = env.getProperty(urlKey);
        String dbUsername = env.getProperty(usernameKey);
        if (dbUrl == null || dbUrl.trim().isEmpty() || dbUsername == null || dbUsername.trim().isEmpty()) {
            return false;
        }

        // 所有条件满足则返回true
        return true;
    }
}
