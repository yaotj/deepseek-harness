package com.chinasofti.huateng.micro.datasource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@ConditionalOnProperty(name = "other.sql.double-datasource", havingValue = "false", matchIfMissing = true)
@PropertySource(value = "classpath:${other.sql.type}.properties", factory = DbPropertyFactory.class)
public class AutoSelectDbType {
}
