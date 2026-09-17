package com.chinasofti.huateng.gatetxnpay.constant;

import org.springframework.util.StringUtils;

/** 三个按字段收口的渠道方言判定：出站交易类型、钱包支付渠道、同行 / 第三方票。 */
public final class GateTxnPayFieldCode {
    /** {@code TRX_TYPE}：出站（普通）。 */
    public static final String TRX_TYPE_EXIT = "02";
    /** {@code TRX_TYPE}：出站（超时 / 特殊）。 */
    public static final String TRX_TYPE_EXIT_SPECIAL = "03";
    /** {@code PAYMENT_VENDOR}：地铁钱包。 */
    public static final String PAYMENT_VENDOR_WALLET = "0B";
    /** {@code CHANNEL_TYPE}：蓝牙渠道，不推公交换乘。 */
    public static final String CHANNEL_TYPE_BLUETOOTH = "01";
    /** {@code COMPANION_FLAG}：同行票。 */
    public static final String COMPANION_FLAG_COMPANION = "Y";
    /** {@code COMPANION_FLAG}：第三方票。 */
    public static final String COMPANION_FLAG_THIRD_PARTY = "C";

    private GateTxnPayFieldCode() {
    }

    /** 是否出站类交易（进站交易只更新票卡状态、不进扣款链路）。 */
    public static boolean isExitTrxType(String trxType) {
        return TRX_TYPE_EXIT.equals(trxType) || TRX_TYPE_EXIT_SPECIAL.equals(trxType);
    }

    /** 是否地铁钱包渠道。 */
    public static boolean isWalletVendor(String paymentVendor) {
        return PAYMENT_VENDOR_WALLET.equalsIgnoreCase(trimToNull(paymentVendor));
    }

    /** 是否蓝牙渠道。 */
    public static boolean isBluetoothChannel(String channelType) {
        return CHANNEL_TYPE_BLUETOOTH.equals(trimToNull(channelType));
    }

    /** 是否同行票或第三方票（两者都不参与钱包累计、也不推公交换乘）。 */
    public static boolean isCompanionOrThirdParty(String companionFlag) {
        String flag = trimToNull(companionFlag);
        return COMPANION_FLAG_COMPANION.equalsIgnoreCase(flag)
                || COMPANION_FLAG_THIRD_PARTY.equalsIgnoreCase(flag);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
