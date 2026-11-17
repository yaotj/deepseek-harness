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

        String doubleDatasourceFlag = env.getProperty("other.sql.double-datasource");
        if (doubleDatasourceFlag == null || !"true".equalsIgnoreCase(doubleDatasourceFlag)) {
            return false;
        }

        Map<String, Object> annotationAttrs = metadata.getAnnotationAttributes(ConditionalOnDoubleDatasource.class.getName());
        if (annotationAttrs == null) {
            return false;
        }
        String datasourceName = (String) annotationAttrs.get("value");
        if (datasourceName == null || datasourceName.trim().isEmpty()) {
            return false;
        }

        String urlKey = String.format("spring.datasource.druid.%s.url", datasourceName);
        String usernameKey = String.format("spring.datasource.druid.%s.username", datasourceName);

        String dbUrl = env.getProperty(urlKey);
        String dbUsername = env.getProperty(usernameKey);
        if (dbUrl == null || dbUrl.trim().isEmpty() || dbUsername == null || dbUsername.trim().isEmpty()) {
            return false;
        }

        return true;
    }
}
