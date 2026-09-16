package com.chinasofti.huateng;

import com.chinasofti.huateng.rpc.EnableRpcKey;
import com.chinasofti.huateng.rpc.EnableRpcRoute;
import com.chinasofti.huateng.rpc.EnableRpcTicket;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 设备前置服务启动类。
 *
 * <p>2026-09-14（ADR-D62）随出站扣费编排迁出 ticket-server，摘掉了三个已无引用的
 * {@code @EnableRpcXxx}：{@code Account}（原为 {@code CardTypeResolver} 查真实卡类型）、
 * {@code GateTxnPay}（原为出站扣费落单）、{@code Para}（原为支付宝 21 键查车站信息）。
 * <b>NEVER 因为「以后可能用到」把它们加回来</b> —— 多一个注解就多一份「本模块会调那个域」
 * 的假象，而 {@code service.*.url} 配错时的表现是响应退化成 UUID retCode，极难定位。</p>
 *
 * <p>2026-09-14（ADR-D67）又摘掉两个：{@code @EnableRpcSecurity} 与
 * {@code @EnableRpcAlipayAccount}。判据是全模块 grep {@code SecurityClient} /
 * {@code AlipayAccountClient} <b>零命中</b> —— 两者都只是
 * {@code @ComponentScan} 到 {@code rpc.security} / {@code rpc.alipay.account} 两个包，
 * 摘掉只少建那两个包里的 Client bean，本模块没有任何注入点。
 * {@code service.security.url} 同批从 {@code application.properties} 删除（原值见该文件注释）。
 * ADR-D62 那轮把 AlipayAccount 记为「保留但零引用、不在本次范围内」，本轮一并收掉。</p>
 *
 * <p>现在剩下的四个注解各有实际注入点，<b>NEVER 再摘</b>：{@code Route}（基础设施，
 * {@code ProxyWebClient} 的动态路由）、{@code Ticket}（{@code GateTransactionHandler} 与
 * {@code QrCodeStatusHandler} 都注 {@code TicketClient}）、{@code Key}（{@code KeySyncHandler}
 * 注 {@code KeyClient}）。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableRpcRoute
@EnableRpcTicket
@EnableRpcKey
public class FepDevServer implements CommandLineRunner {

    /**
     * 设备前置服务启动入口。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(FepDevServer.class, args);
    }

    /**
     * 服务启动后执行的初始化逻辑。
     *
     * @param args 启动参数
     */
    @Override
    public void run(String... args) {
    }
}
