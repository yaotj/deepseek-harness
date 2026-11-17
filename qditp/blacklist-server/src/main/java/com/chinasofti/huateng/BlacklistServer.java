package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcGateTxnPay;
import com.chinasofti.huateng.rpc.EnableRpcPaySign;
import com.chinasofti.huateng.rpc.EnableRpcRoute;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@EnableRpcGateTxnPay
@EnableRpcPaySign
@EnableRpcRoute
public class BlacklistServer {

    public static void main(String[] args) {
        SpringApplication.run(BlacklistServer.class, args);
    }
}
