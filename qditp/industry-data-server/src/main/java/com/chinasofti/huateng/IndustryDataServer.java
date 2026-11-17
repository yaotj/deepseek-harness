package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.EnableRpcRoute;
import com.chinasofti.huateng.rpc.EnableRpcSecurity;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableRpcRoute
@EnableRpcSecurity
public class IndustryDataServer implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(IndustryDataServer.class, args);
    }

    @Override
    public void run(String... args) {
    }
}
