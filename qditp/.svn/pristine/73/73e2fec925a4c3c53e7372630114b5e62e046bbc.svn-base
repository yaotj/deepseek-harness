package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.EnableRpcAccount;
import com.chinasofti.huateng.rpc.EnableRpcGateTxnPay;
import com.chinasofti.huateng.rpc.EnableRpcRoute;
import com.chinasofti.huateng.rpc.EnableRpcSecurity;
import com.chinasofti.huateng.rpc.EnableRpcTicket;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 设备前置服务启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRpcRoute
@EnableRpcAccount
@EnableRpcSecurity
@EnableRpcTicket
@EnableRpcGateTxnPay
public class FepDevServer implements CommandLineRunner {

    /**
     * 设备前置服务启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FepDevServer.class, args);
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
