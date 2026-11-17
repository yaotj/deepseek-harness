package com.chinasofti.huateng.gatetxnpay.constant;

import org.springframework.util.StringUtils;

public final class GateTxnPayFieldCode {
    public static final String TRX_TYPE_EXIT = "02";
    public static final String TRX_TYPE_EXIT_SPECIAL = "03";
    public static final String PAYMENT_VENDOR_WALLET = "0B";
    public static final String CHANNEL_TYPE_BLUETOOTH = "01";
    public static final String COMPANION_FLAG_COMPANION = "Y";
    public static final String COMPANION_FLAG_THIRD_PARTY = "C";

    private GateTxnPayFieldCode() {
    }

    public static boolean isExitTrxType(String trxType) {
        return TRX_TYPE_EXIT.equals(trxType) || TRX_TYPE_EXIT_SPECIAL.equals(trxType);
    }

    public static boolean isWalletVendor(String paymentVendor) {
        return PAYMENT_VENDOR_WALLET.equalsIgnoreCase(trimToNull(paymentVendor));
    }

    public static boolean isBluetoothChannel(String channelType) {
        return CHANNEL_TYPE_BLUETOOTH.equals(trimToNull(channelType));
    }

    public static boolean isCompanionOrThirdParty(String companionFlag) {
        String flag = trimToNull(companionFlag);
        return COMPANION_FLAG_COMPANION.equalsIgnoreCase(flag)
                || COMPANION_FLAG_THIRD_PARTY.equalsIgnoreCase(flag);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
