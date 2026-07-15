package com.chinasofti.huateng;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class CollectPayServer {
    public static void main(String[] args) {
        SpringApplication.run(CollectPayServer.class, args);
    }
}
