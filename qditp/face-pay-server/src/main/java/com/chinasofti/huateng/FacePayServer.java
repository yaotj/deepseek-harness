package com.chinasofti.huateng;

import com.chinasofti.huateng.facepay.service.F2fRefundRetryPolicy;
import com.chinasofti.huateng.rpc.EnableRpcAccount;
import com.chinasofti.huateng.rpc.EnableRpcGateTxnPay;
import com.chinasofti.huateng.rpc.EnableRpcRecon;
import com.chinasofti.huateng.rpc.EnableRpcTicket;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 当面付服务（TVM / BOM / APP 非现金收款）。 */
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(F2fRefundRetryPolicy.class)
@EnableRpcTicket
@EnableRpcAccount
@EnableRpcRecon
@EnableRpcGateTxnPay
public class FacePayServer {
    public static void main(String[] args) {
        SpringApplication.run(FacePayServer.class, args);
    }
}
