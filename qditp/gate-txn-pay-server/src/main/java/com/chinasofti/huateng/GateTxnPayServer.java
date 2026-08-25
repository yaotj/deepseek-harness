package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcPaySign;
import com.chinasofti.huateng.rpc.EnableRpcRoute;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@EnableRpcRoute
@EnableRpcPaySign
@EnableAsync
public class GateTxnPayServer {
    public static void main(String[] args) {
        SpringApplication.run(GateTxnPayServer.class, args);
    }
}
