package com.chinasofti.huateng.micro.datasource;

import com.alibaba.druid.filter.Filter;
import com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure;
import com.alibaba.druid.stat.DruidStatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;

@Configuration
@PropertySource(value = "classpath:sql.properties")
@AutoConfigureBefore(DruidDataSourceAutoConfigure.class)
public class SqlConfiguration {

    public static Logger log = LoggerFactory.getLogger(SqlConfiguration.class);

    @Bean
    @ConditionalOnProperty(name = "other.sql.audit", havingValue = "true", matchIfMissing = false)
    public Filter sqlFilter() {
        return new SqlAudit();
    }

    @Bean
    @ConditionalOnProperty(name = "other.sql.double-datasource", havingValue = "false", matchIfMissing = true)
    @ConfigurationProperties("spring.datasource.druid")
    public DataSource dataSource() {
        log.info("Load DruidDataSource from config spring.datasource.druid");
        return new DruidDataSourceWrapper1(null, null);
    }


    @Scheduled(cron = "${other.monitor.resetAllMetersCron}")
    @Async
    public void resetAllDruidStatData() {
        log.info("reset all druidStatData");
        DruidStatService statService = DruidStatService.getInstance();
        log.info(statService.service("/datasource.json"));
        log.info(statService.service("/webapp.json"));
        log.info(statService.service("/weburi.json?orderBy=URI&orderType=desc&page=1&perPageCount=1000000&"));
        statService.service("/reset-all.json");
    }

}



