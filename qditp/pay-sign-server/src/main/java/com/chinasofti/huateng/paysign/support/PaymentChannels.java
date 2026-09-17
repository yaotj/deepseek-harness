package com.chinasofti.huateng.paysign.support;

import static com.chinasofti.huateng.paysign.support.PaySignValues.normalizeVendor;

import com.chinasofti.huateng.paysign.constant.PaymentVendorEnum;

/** 「这笔请求走的是哪个支付渠道」的**唯一判定入口**（2026-09-16，ADR-D108）。 */
public final class PaymentChannels {

    /** 钱包渠道号。**对外只暴露 {@link #walletCode()}**，字段本身私有，避免又被别处 import 成第 6 份副本。 */
    private static final String WALLET = PaymentVendorEnum.WALLET.getCode();

    private PaymentChannels() {
    }

    /** 判断报文里的 paymentVendor 是否钱包渠道。 */
    public static boolean isWallet(String paymentVendor) {
        return WALLET.equals(normalizeVendor(paymentVendor));
    }

    /** 把报文里的 paymentVendor 归类成**处理类别**，给入口级路径选择用。 */
    public static PaymentChannel classify(String paymentVendor) {
        String normalized = normalizeVendor(paymentVendor);
        return WALLET.equals(normalized)
                ? new PaymentChannel.Wallet(normalized)
                : new PaymentChannel.Contracted(normalized);
    }

    /** 钱包渠道号本身。仅给「要把渠道号当值写出去」的地方用 —— 例如审计流水的 paymentVendor 段。 */
    public static String walletCode() {
        return WALLET;
    }
}
