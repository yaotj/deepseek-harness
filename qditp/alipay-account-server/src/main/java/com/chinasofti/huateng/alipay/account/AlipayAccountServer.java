package com.chinasofti.huateng.alipay.account;

import com.chinasofti.huateng.rpc.EnableRpcRoute;
import com.chinasofti.huateng.rpc.EnableRpcTicket;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;

/**
 * 支付宝账户服务启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@ComponentScan(basePackages = {
        "com.chinasofti.huateng.alipay.account",
        "com.chinasofti.huateng.rpc"
})
@EnableRpcRoute
@EnableRpcTicket
public class AlipayAccountServer implements CommandLineRunner {

    /**
     * 支付宝账户服务启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AlipayAccountServer.class, args);
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
