package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcCollectPay;
import com.chinasofti.huateng.rpc.EnableRpcPaySign;
import com.chinasofti.huateng.rpc.EnableRpcAccount;
import com.chinasofti.huateng.rpc.EnableRpcPara;
import com.chinasofti.huateng.rpc.EnableRpcRecon;
import com.chinasofti.huateng.rpc.EnableRpcRoute;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@EnableRpcRoute
@EnableRpcPaySign
@EnableRpcAccount
@EnableRpcPara
@EnableRpcRecon
@EnableRpcCollectPay
@EnableAsync
@EnableScheduling
public class GateTxnPayServer {
    public static void main(String[] args) {
        SpringApplication.run(GateTxnPayServer.class, args);
    }
}
