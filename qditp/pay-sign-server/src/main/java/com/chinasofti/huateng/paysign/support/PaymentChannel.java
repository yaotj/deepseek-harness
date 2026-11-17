package com.chinasofti.huateng.paysign.support;

public sealed interface PaymentChannel {

    record Wallet(String code) implements PaymentChannel {
    }

    record Contracted(String code) implements PaymentChannel {
    }

    String code();
}
