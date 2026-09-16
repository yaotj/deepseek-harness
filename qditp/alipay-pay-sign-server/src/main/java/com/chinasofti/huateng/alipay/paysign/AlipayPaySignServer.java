package com.chinasofti.huateng.alipay.paysign;

import com.chinasofti.huateng.rpc.EnableRpcBlacklist;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import com.chinasofti.huateng.rpc.EnableRpcAlipayAccount;
import com.chinasofti.huateng.rpc.EnableRpcGateTxnPay;
import com.chinasofti.huateng.rpc.EnableRpcPara;
import com.chinasofti.huateng.rpc.EnableRpcTicket;

/**
 * 支付宝签约与支付服务启动类。
 *
 * <p>职责：处理支付宝交通乘车码场景的签约、支付、退款、查询及黑名单通知。
 * 通过 RPC 依赖外部能力：
 * <ul>
 *   <li>{@link EnableRpcAlipayAccount} - 查询/更新支付宝账户信息</li>
 *   <li>{@link EnableRpcPara} - 查询车站、线路等公共参数</li>
 *   <li>{@link EnableRpcTicket} - 票务相关能力</li>
 *   <li>{@link EnableRpcGateTxnPay} - 支付结果回调把 GATE_TXN_PAY.DEBIT_STATUS 收敛到终态</li>
 * </ul>
 */
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
