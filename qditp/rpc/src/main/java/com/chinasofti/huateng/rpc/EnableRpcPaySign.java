package com.chinasofti.huateng.rpc;

import org.springframework.context.annotation.ComponentScan;

import java.lang.annotation.*;


@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ComponentScan(basePackages = {"com.chinasofti.huateng.rpc.paySign", "com.chinasofti.huateng.rpc.alipay.paysign"})
public @interface EnableRpcPaySign {
}
