package com.chinasofti.huateng.fep.alipay;

import com.chinasofti.huateng.rpc.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;


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


    public static void main(String[] args) {
        SpringApplication.run(FepAlipayServer.class, args);
    }
}
