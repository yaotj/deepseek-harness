package com.chinasofti.huateng.alipay.paysign;

import com.chinasofti.huateng.rpc.EnableRpcBlacklist;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import com.chinasofti.huateng.rpc.EnableRpcAlipayAccount;
import com.chinasofti.huateng.rpc.EnableRpcGateTxnPay;
import com.chinasofti.huateng.rpc.EnableRpcPara;
import com.chinasofti.huateng.rpc.EnableRpcTicket;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRpcAlipayAccount
@EnableRpcPara
@EnableRpcTicket
@EnableRpcBlacklist
@EnableRpcGateTxnPay
public class AlipayPaySignServer {

    public static void main(String[] args) {
        SpringApplication.run(AlipayPaySignServer.class, args);
    }
}
