package com.chinasofti.huateng.rpc;

import org.springframework.context.annotation.ComponentScan;

import java.lang.annotation.*;

/**
 * 启用 trans-query-server（交易查询，9113）客户端。
 *
 * <p>与 {@link EnableRpcTicket} <b>不互斥、通常成对出现</b>：交易列表 / 统计 / 详情走本注解，
 * 支付宝行程与乘车码状态机等仍留在 ticket-server、仍需 {@code @EnableRpcTicket}。
 * 加了本注解 MUST 同批在 {@code application.properties} 补 {@code service.transQuery.url}，
 * 否则 baseUrl 退化成默认服务名 {@code trans-query-service}、集群里 DNS 解析不到。</p>
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ComponentScan(basePackages = {"com.chinasofti.huateng.rpc.transquery"})
public @interface EnableRpcTransQuery {
}
