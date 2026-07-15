package com.chinasofti.huateng.fep.alipay.notify;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 支付宝通知推送服务启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class FepAlipayNotifyServer implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(FepAlipayNotifyServer.class, args);
    }

    @Override
    public void run(String... args) {
    }
}
