package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 逻辑卡号池服务启动类。 */
@SpringBootApplication
@EnableDefaultMybatisAutoConfig
public class CardPoolServer {

    /**
     * 启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CardPoolServer.class, args);
    }
}
