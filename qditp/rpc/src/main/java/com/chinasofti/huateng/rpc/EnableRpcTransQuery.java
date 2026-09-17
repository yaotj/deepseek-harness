package com.chinasofti.huateng.rpc;

import org.springframework.context.annotation.ComponentScan;

import java.lang.annotation.*;

/**
 * 启用 trans-query-server（交易查询，9113）客户端。
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ComponentScan(basePackages = {"com.chinasofti.huateng.rpc.transquery"})
public @interface EnableRpcTransQuery {
}
