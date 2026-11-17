package com.chinasofti.huateng.paysign.support;

import static com.chinasofti.huateng.paysign.support.PaySignValues.normalizeVendor;

import com.chinasofti.huateng.paysign.constant.PaymentVendorEnum;

public final class PaymentChannels {

    private static final String WALLET = PaymentVendorEnum.WALLET.getCode();

    private PaymentChannels() {
    }

    public static boolean isWallet(String paymentVendor) {
        return WALLET.equals(normalizeVendor(paymentVendor));
    }

    public static PaymentChannel classify(String paymentVendor) {
        String normalized = normalizeVendor(paymentVendor);
        return WALLET.equals(normalized)
                ? new PaymentChannel.Wallet(normalized)
                : new PaymentChannel.Contracted(normalized);
    }

    public static String walletCode() {
        return WALLET;
    }
}
