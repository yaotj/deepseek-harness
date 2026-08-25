package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcIndustryData;
import com.chinasofti.huateng.rpc.EnableRpcAccount;
import com.chinasofti.huateng.rpc.EnableRpcPara;
import com.chinasofti.huateng.rpc.EnableRpcRoute;
import com.chinasofti.huateng.rpc.EnableRpcSecurity;
import com.chinasofti.huateng.rpc.EnableRpcAlipayAccount;
import com.chinasofti.huateng.rpc.EnableRpcGateTxnPay;
import com.chinasofti.huateng.rpc.EnableRpcPaySign;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@EnableRpcRoute
@EnableRpcSecurity
@EnableRpcIndustryData
@EnableRpcAccount
@EnableRpcAlipayAccount
@EnableRpcPara
@EnableRpcGateTxnPay
@EnableRpcPaySign
public class TicketServer implements CommandLineRunner {
    public static void main(String[] args) {
        SpringApplication.run(TicketServer.class, args);
    }

    @Override
    public void run(String... args) {
    }
}
