package com.chinasofti.huateng.fep.alipay;

import com.chinasofti.huateng.rpc.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 支付宝入口服务启动类。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRpcAccount
@EnableRpcAlipayAccount
@EnableRpcBlacklist
@EnableRpcPaySign
@EnableRpcTicket
@EnableRpcIndustryData
@EnableRpcGateTxnPay
public class FepAlipayServer {

    /**
     * 支付宝入口服务启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FepAlipayServer.class, args);
    }
}
