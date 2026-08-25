package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.EnableRpcAccount;
import com.chinasofti.huateng.rpc.EnableRpcTicket;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties
@EnableRpcTicket
@EnableRpcAccount
public class CollectPayServer {
    public static void main(String[] args) {
        SpringApplication.run(CollectPayServer.class, args);
    }
}
