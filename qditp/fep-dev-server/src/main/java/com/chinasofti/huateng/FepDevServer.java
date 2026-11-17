package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.EnableRpcKey;
import com.chinasofti.huateng.rpc.EnableRpcRoute;
import com.chinasofti.huateng.rpc.EnableRpcTicket;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableRpcRoute
@EnableRpcTicket
@EnableRpcKey
public class FepDevServer implements CommandLineRunner {


    public static void main(String[] args) {
        SpringApplication.run(FepDevServer.class, args);
    }


    @Override
    public void run(String... args) {
    }
}
