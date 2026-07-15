package com.chinasofti.huateng.alipay.paysign;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import com.chinasofti.huateng.rpc.EnableRpcAlipayAccount;

/**
 * 支付宝签约服务启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRpcAlipayAccount
public class AlipayPaySignServer implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(AlipayPaySignServer.class, args);
    }

    @Override
    public void run(String... args) {
    }
}
