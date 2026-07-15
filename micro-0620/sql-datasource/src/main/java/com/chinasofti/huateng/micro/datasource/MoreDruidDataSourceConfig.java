package com.chinasofti.huateng.micro.datasource;

import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure;
import com.chinasofti.huateng.micro.datasource.conditional.ConditionalOnDoubleDatasource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

@Configuration
@AutoConfigureBefore(DruidDataSourceAutoConfigure.class)
public class MoreDruidDataSourceConfig implements EnvironmentAware {
    public static Logger log = LoggerFactory.getLogger(MoreDruidDataSourceConfig.class);

    private Environment env;

    @Override
    public void setEnvironment(Environment environment) {
        this.env = environment;
    }

    public Environment getEnv() {
        return env;
    }

    @ConditionalOnDoubleDatasource("database1")
    @Primary
    @Bean
    @ConfigurationProperties("spring.datasource.druid.database1")
    public DataSource database1() {
        log.info("Load DruidDataSource from config spring.datasource.druid.database1");
        return new DruidDataSourceWrapper1(env, "database1");
    }

    @ConditionalOnDoubleDatasource("database2")
    @Bean
    @ConfigurationProperties("spring.datasource.druid.database2")
    public DataSource database2() {
        log.info("Load DruidDataSource from config spring.datasource.druid.database2");
        return new DruidDataSourceWrapper1(env, "database2");
    }

    @ConditionalOnDoubleDatasource("database3")
    @Bean
    @ConfigurationProperties("spring.datasource.druid.database3")
    public DataSource database3() {
        log.info("Load DruidDataSource from config spring.datasource.druid.database3");
        return new DruidDataSourceWrapper1(env, "database3");
    }

    @ConditionalOnDoubleDatasource("database4")
    @Bean
    @ConfigurationProperties("spring.datasource.druid.database4")
    public DataSource database4() {
        log.info("Load DruidDataSource from config spring.datasource.druid.database4");
        return new DruidDataSourceWrapper1(env, "database4");
    }

    @ConditionalOnDoubleDatasource("database5")
    @Bean
    @ConfigurationProperties("spring.datasource.druid.database5")
    public DataSource database5() {
        log.info("Load DruidDataSource from config spring.datasource.druid.database5");
        return new DruidDataSourceWrapper1(env, "database5");
    }

}
