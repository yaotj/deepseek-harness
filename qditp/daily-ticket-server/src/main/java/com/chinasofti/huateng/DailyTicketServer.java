package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 日票服务启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties
@EnableDefaultMybatisAutoConfig
public class DailyTicketServer {
    public static void main(String[] args) {
        SpringApplication.run(DailyTicketServer.class, args);
    }
}
