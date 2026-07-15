package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@EnableRpcRoute
public class WalletServer implements CommandLineRunner {
    public static Logger log = LoggerFactory.getLogger(WalletServer.class);

    public static void main(String[] args) {
        SpringApplication.run(WalletServer.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
    }
}
