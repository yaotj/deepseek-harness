package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * APP 前置服务启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRpcRoute
@EnableRpcBlacklist
@EnableRpcKey
@EnableRpcAccount
@EnableRpcSecurity
@EnableRpcPaySign
@EnableRpcTicket
@EnableRpcIndustryData
@EnableRpcPara
@EnableRpcDailyTicket
public class FepAppServer implements CommandLineRunner {

    /**
     * APP 前置服务启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FepAppServer.class, args);
    }

    /**
     * 服务启动后执行的初始化逻辑。
     *
     * @param args 启动参数
     */
    @Override
    public void run(String... args) {
    }
}
