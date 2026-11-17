package com.chinasofti.huateng.paysign.port;


public sealed interface AccountQuery<T> {

    record Found<T>(T value) implements AccountQuery<T> {
    }

    record NotFound<T>(String retCode, String retMsg) implements AccountQuery<T> {
    }

    record Unreachable<T>(Throwable cause) implements AccountQuery<T> {
    }
}
