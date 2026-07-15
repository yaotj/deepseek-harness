package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.EnableRpcRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRpcRoute
public class AccSecureServer implements CommandLineRunner {
    public static Logger log = LoggerFactory.getLogger(AccSecureServer.class);

    public static void main(String[] args) {
        SpringApplication.run(AccSecureServer.class, args);
    }

    @Override
    public void run(String... args) {
    }
}
