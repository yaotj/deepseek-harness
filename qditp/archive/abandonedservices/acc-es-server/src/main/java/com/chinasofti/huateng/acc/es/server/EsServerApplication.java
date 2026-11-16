package com.chinasofti.huateng.acc.es.server;

import com.chinasofti.huateng.acc.es.server.netty.service.NettyServer;
import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @program: spring-cloud-acc
 * @description:
 * @author: fc
 * @create: 2020-09-10 16:16
 */
@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@MapperScan("com.chinasofti.huateng.acc.es.server.mapper")
@EnableAsync
@Slf4j
public class EsServerApplication implements CommandLineRunner {

    @Autowired
    private NettyServer nettyServer;

    public static void main(String[] args) {
        SpringApplication.run(EsServerApplication.class,args);
    }

    @Override
    public void run(String... args) throws Exception {
        nettyServer.start();
        log.info("启动。。。。。。。。。。。。。。。。。。。。。。。。。。。。。。。。。。。。。。。。。");
    }
}
