package com.chinasofti.huateng.alipay.account;

import com.chinasofti.huateng.rpc.EnableRpcRoute;
import com.chinasofti.huateng.rpc.EnableRpcTicket;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.chinasofti.huateng.rpc.EnableRpcCardPool;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;


@SpringBootApplication
@EnableRpcCardPool
@ConfigurationPropertiesScan
@ComponentScan(basePackages = {
        "com.chinasofti.huateng.alipay.account",
        "com.chinasofti.huateng.rpc"
})
@EnableRpcRoute
@EnableRpcTicket
public class AlipayAccountServer implements CommandLineRunner {


    public static void main(String[] args) {
        SpringApplication.run(AlipayAccountServer.class, args);
    }


    @Override
    public void run(String... args) {
    }
}
