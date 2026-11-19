package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcBlacklist;
import com.chinasofti.huateng.rpc.EnableRpcRoute;
import com.chinasofti.huateng.rpc.EnableRpcSecurity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@EnableRpcRoute
@EnableRpcSecurity
@EnableRpcBlacklist
public class ParaServer {

    public static void main(String[] args) {
        SpringApplication.run(ParaServer.class, args);
    }
}
