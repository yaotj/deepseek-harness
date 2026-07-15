package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcPaySign;
import com.chinasofti.huateng.rpc.EnableRpcRoute;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@EnableRpcRoute
@EnableRpcPaySign
public class GateTxnPayServer {
    public static void main(String[] args) {
        SpringApplication.run(GateTxnPayServer.class, args);
    }
}
