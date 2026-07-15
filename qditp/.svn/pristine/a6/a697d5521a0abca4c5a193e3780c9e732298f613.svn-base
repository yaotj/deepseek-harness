package com.chinasofti.huateng.micro.datasource;

import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceWrapper;
import org.springframework.core.env.Environment;

public class DruidDataSourceWrapper1 extends DruidDataSourceWrapper {

    Environment env;

    String name;

    public DruidDataSourceWrapper1(Environment env, String name) {
        this.env = env;
        this.name = name;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        setDefaultIfNull();
        super.afterPropertiesSet();
        super.resolveDriver();
        super.getConnection();
    }

    private void setDefaultIfNull() {
        if (env == null) {
            return;
        }
        String defaultPrefix = "spring.datasource.druid.";
        String prefix = "spring.datasource.druid." + name + ".";
        int maxActive = env.getProperty(prefix + "max-active", Integer.class, env.getProperty(defaultPrefix + "max-active", Integer.class));
        setMaxActive(maxActive);

        int initialSize = env.getProperty(prefix + "initial-size", Integer.class, env.getProperty(defaultPrefix + "initial-size", Integer.class));
        setInitialSize(initialSize);

        int minIdle = env.getProperty(prefix + "min-idle", Integer.class, env.getProperty(defaultPrefix + "min-idle", Integer.class));
        setMinIdle(minIdle);

        int maxWait = env.getProperty(prefix + "max-wait", Integer.class, env.getProperty(defaultPrefix + "max-wait", Integer.class));
        setMaxWait(maxWait);

        boolean poolPreparedStatements = env.getProperty(prefix + "pool-prepared-statements", Boolean.class, env.getProperty(defaultPrefix + "pool-prepared-statements", Boolean.class));
        setPoolPreparedStatements(poolPreparedStatements);

        int maxPoolPreparedStatementPerConnectionSize = env.getProperty(prefix + "max-pool-prepared-statement-per-connection-size", Integer.class, env.getProperty(defaultPrefix + "max-pool-prepared-statement-per-connection-size", Integer.class));
        setMaxPoolPreparedStatementPerConnectionSize(maxPoolPreparedStatementPerConnectionSize);

        String validationQuery = env.getProperty(prefix + "validation-query", String.class, env.getProperty(defaultPrefix + "validation-query", String.class));
        setValidationQuery(validationQuery);

        int validationQueryTimeout = env.getProperty(prefix + "validation-query-timeout", Integer.class, env.getProperty(defaultPrefix + "validation-query-timeout", Integer.class));
        setValidationQueryTimeout(validationQueryTimeout);

        boolean testOnBorrow = env.getProperty(prefix + "test-on-borrow", Boolean.class, env.getProperty(defaultPrefix + "test-on-borrow", Boolean.class));
        setTestOnBorrow(testOnBorrow);

        boolean testOnReturn = env.getProperty(prefix + "test-on-return", Boolean.class, env.getProperty(defaultPrefix + "test-on-return", Boolean.class));
        setTestOnReturn(testOnReturn);

        boolean testWhileIdle = env.getProperty(prefix + "test-while-idle", Boolean.class, env.getProperty(defaultPrefix + "test-while-idle", Boolean.class));
        setTestWhileIdle(testWhileIdle);

        long timeBetweenEvictionRunsMillis = env.getProperty(prefix + "time-between-eviction-runs-millis", Long.class, env.getProperty(defaultPrefix + "time-between-eviction-runs-millis", Long.class));
        setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);

        long minEvictableIdleTimeMillis = env.getProperty(prefix + "min-evictable-idle-time-millis", Long.class, env.getProperty(defaultPrefix + "min-evictable-idle-time-millis", Long.class));
        setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);

    }
}
