package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcSecurity;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@EnableRpcSecurity
public class KeyServer {

    public static void main(String[] args) {
        SpringApplication.run(KeyServer.class, args);
    }
}
