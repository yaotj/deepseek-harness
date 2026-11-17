package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcRecon;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties
@EnableDefaultMybatisAutoConfig
@EnableRpcRecon
public class DailyTicketServer {
    public static void main(String[] args) {
        SpringApplication.run(DailyTicketServer.class, args);
    }
}
