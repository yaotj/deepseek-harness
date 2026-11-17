package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;


@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRpcRoute
@EnableRpcBlacklist
@EnableRpcKey
@EnableRpcAccount
@EnableRpcSecurity
@EnableRpcPaySign
@EnableRpcTicket
@EnableRpcIndustryData
@EnableRpcPara
@EnableRpcDailyTicket
@EnableRpcCollectPay
@EnableRpcGateTxnPay
@EnableRpcTransQuery
@EnableRpcFacePay
public class FepAppServer implements CommandLineRunner {


    public static void main(String[] args) {
        SpringApplication.run(FepAppServer.class, args);
    }


    @Override
    public void run(String... args) {
    }
}
