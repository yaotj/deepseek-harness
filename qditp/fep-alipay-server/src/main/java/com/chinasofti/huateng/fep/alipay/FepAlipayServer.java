package com.chinasofti.huateng.fep.alipay;

import com.chinasofti.huateng.rpc.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 支付宝入口服务启动类。
 *
 * <p>{@code @EnableRpcGateTxnPay} 已随支付宝行程查询迁出（1.0.5 起该链路走 trans-query-server）而摘除：
 * 摘除前已确认 {@code GateTxnPayClient} 在本模块**零引用**。{@code @EnableRpcTicket} **保留**，
 * 因为 {@code AlipayApplicationServiceImpl} 仍在用 {@code TicketClient}。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRpcAccount
@EnableRpcAlipayAccount
@EnableRpcBlacklist
@EnableRpcPaySign
@EnableRpcTicket
@EnableRpcIndustryData
@EnableRpcTransQuery
public class FepAlipayServer {

    /**
     * 支付宝入口服务启动入口。
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FepAlipayServer.class, args);
    }
}
