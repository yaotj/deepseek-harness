package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.EnableRpcAccount;
import com.chinasofti.huateng.rpc.EnableRpcGateTxnPay;
import com.chinasofti.huateng.rpc.EnableRpcRecon;
import com.chinasofti.huateng.rpc.EnableRpcTicket;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 当面付服务（TVM / BOM / APP 非现金收款）。
 *
 * <p>本服务是 collect-pay-server 的同契约重写，设计见
 * docs/architecture/face-pay-refactor.md。切流量前两者并存，切换方式是改 K8s
 * Service selector，因此端口与 spring.application.name 与旧服务保持一致。
 *
 * <p>依赖的下游与 collect-pay-server 相同：ticket-server（IF5A 票卡透传）、
 * account-server（用户与支付通道）。新增下游 MUST 在 rpc 模块补 Client，
 * NEVER 在本模块内自建 HTTP 调用。
 *
 * <p>{@code @EnableRpcRecon} 是本服务作为日终对账第 4 个抽取源所需（{@code ReconExportService}
 * 注入 {@code ReconPartUploader} / {@code ReconClient}，两者都在 {@code rpc.recon} 包内，
 * 不加这个注解就扫不到、启动即报注入失败）。同时 MUST 配 {@code service.recon.url}，
 * 缺键时 rpc 会落到默认服务名、DNS 解析不到，分片上送全部失败。
 *
 * <p>{@code @EnableRpcGateTxnPay} 是补款收敛所需（2026-09-16 新增）：
 * {@code SupplementPayCenterFlow} 注入 {@code GateTxnPayClient} 调
 * {@code POST /internal/gate-txn-pay/debit/converge} 收敛原过闸订单 —— 那张表的 owner 是
 * gate-txn-pay-server，本模块此前是直写、已改 RPC。同样 MUST 配 {@code service.gateTxnPay.url}，
 * 缺键会落到默认服务名、解析不到，表现为补款支付成功但明细永远结不清。
 */
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties
@EnableRpcTicket
@EnableRpcAccount
@EnableRpcRecon
@EnableRpcGateTxnPay
public class FacePayServer {
    public static void main(String[] args) {
        SpringApplication.run(FacePayServer.class, args);
    }
}
