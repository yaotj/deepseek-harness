package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDefaultMybatisAutoConfig
public class CardPoolServer {


    public static void main(String[] args) {
        SpringApplication.run(CardPoolServer.class, args);
    }
}
