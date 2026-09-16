package com.chinasofti.huateng.rpc;

import org.springframework.context.annotation.ComponentScan;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Enables the reconciliation RPC client (part upload / batch declaration). */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ComponentScan(basePackages = "com.chinasofti.huateng.rpc.recon")
public @interface EnableRpcRecon { }
