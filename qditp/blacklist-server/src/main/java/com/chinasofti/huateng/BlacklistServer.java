package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcRoute;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 黑名单服务启动类。
 */
@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@EnableRpcRoute
public class BlacklistServer {
    /**
     * 黑名单服务启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(BlacklistServer.class, args);
    }
}
