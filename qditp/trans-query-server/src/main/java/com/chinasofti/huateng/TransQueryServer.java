package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.EnableRpcDailyTicket;
import com.chinasofti.huateng.rpc.EnableRpcGateTxnPay;
import com.chinasofti.huateng.rpc.EnableRpcPara;
import com.chinasofti.huateng.rpc.EnableRpcPaySign;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 交易查询服务（纯转发壳）。 */
@SpringBootApplication
@EnableRpcGateTxnPay
@EnableRpcPaySign
@EnableRpcPara
@EnableRpcDailyTicket
public class TransQueryServer {
    public static void main(String[] args) {
        SpringApplication.run(TransQueryServer.class, args);
    }
}
