package com.chinasofti.huateng;

import com.chinasofti.huateng.micro.mybatis.adaptor.EnableDefaultMybatisAutoConfig;
import com.chinasofti.huateng.rpc.EnableRpcPaySign;
import com.chinasofti.huateng.rpc.EnableRpcAccount;
import com.chinasofti.huateng.rpc.EnableRpcPara;
import com.chinasofti.huateng.rpc.EnableRpcRecon;
import com.chinasofti.huateng.rpc.EnableRpcRoute;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 2026-10-13 collect-pay 下线后清理：原先 `@EnableRpcCollectPay` 与
 * `application.properties` 的 `service.collectPay.url` 只是装配了
 * {@code rpc.collectpay.CollectPayClient} bean，但 gate-txn-pay 全模块从未
 * 注入或调用它（属「接上 bean 但无人用」的死配置）；且 collect-pay 已
 * 0 pod 下线。文档（{@code docs/business/tvm-bom-pay.md}、
 * {@code docs/ops/生产环境清单.md}）曾记「gate-txn-pay 是
 * {@code /internal/app-order/*} 唯一合法调用方」，本轮按 §0「以代码为准」
 * 重新复核后推翻：实际调用方为零、该端点属未落地功能。
 */
@SpringBootApplication
@EnableDefaultMybatisAutoConfig
@EnableRpcRoute
@EnableRpcPaySign
@EnableRpcAccount
@EnableRpcPara
@EnableRpcRecon
@EnableAsync
@EnableScheduling
public class GateTxnPayServer {
    public static void main(String[] args) {
        SpringApplication.run(GateTxnPayServer.class, args);
    }
}
