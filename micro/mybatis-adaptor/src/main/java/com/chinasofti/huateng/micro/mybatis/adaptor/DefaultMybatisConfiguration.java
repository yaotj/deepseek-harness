package com.chinasofti.huateng.micro.mybatis.adaptor;

import com.github.pagehelper.PageInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Properties;

@Configuration
@PropertySource("classpath:persistent.properties")
@EnableTransactionManagement
public class DefaultMybatisConfiguration {
    public static Logger log = LoggerFactory.getLogger(DefaultMybatisConfiguration.class);

    @Value("${other.sql.page.type:}")
    String druid_jdbc_page_type;

    @ConditionalOnProperty(name = "other.sql.double-datasource", havingValue = "false")
    @ConditionalOnMissingBean(type = {"com.github.pagehelper.PageInterceptor"})
    @Bean
    public PageInterceptor pageInterceptor() {
        PageInterceptor pageInterceptor = new PageInterceptor();
        Properties properties = new Properties();
        properties.setProperty("helperDialect", druid_jdbc_page_type);
        properties.setProperty("reasonable", "true");
        pageInterceptor.setProperties(properties);
        return pageInterceptor;
    }


}
