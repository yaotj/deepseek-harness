package com.chinasofti.huateng.fep.acc;

import com.chinasofti.huateng.rpc.EnableRpcAccount;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * ACC 前置服务启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRpcAccount
public class FepAccServer {
    public static void main(String[] args) {
        SpringApplication.run(FepAccServer.class, args);
    }
}
