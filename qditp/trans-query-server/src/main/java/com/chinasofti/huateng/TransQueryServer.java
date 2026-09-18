package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.EnableRpcDailyTicket;
import com.chinasofti.huateng.rpc.EnableRpcGateTxnPay;
import com.chinasofti.huateng.rpc.EnableRpcPara;
import com.chinasofti.huateng.rpc.EnableRpcPaySign;
import com.chinasofti.huateng.rpc.EnableRpcTicket;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 交易查询服务（纯转发壳）。
 *
 * <p>{@code @EnableRpcPaySign} 同时扫了 {@code rpc.paySign} 与 {@code rpc.alipay.paysign} 两个包，
 * 因此支付宝行程列表用的 {@code AlipayPaySignClient} 由它装配，**没有** {@code @EnableRpcAlipayPaySign} 这个注解。
 */
@SpringBootApplication
@EnableRpcGateTxnPay
@EnableRpcPaySign
@EnableRpcPara
@EnableRpcDailyTicket
@EnableRpcTicket
public class TransQueryServer {
    public static void main(String[] args) {
        SpringApplication.run(TransQueryServer.class, args);
    }
}
