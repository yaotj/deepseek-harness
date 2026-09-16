package com.chinasofti.huateng.gatetxnpay.constant;

import org.springframework.util.StringUtils;

/**
 * 三个**按字段**收口的渠道方言判定：出站交易类型、钱包支付渠道、同行 / 第三方票。
 *
 * <p>只收这三个，是因为只有这三个是**同一个字段上的同一个判定**被抄了两遍以上：
 * {@code TRX_TYPE ∈ {02,03}} 在 `GateTxnPayServiceImpl` 与 `MetroTransferPushTaskProcessor` 各一份、
 * {@code PAYMENT_VENDOR='0B'} 在 `FareCalculator` 与 `MetroTransferPushTaskProcessor` 各一份、
 * {@code COMPANION_FLAG ∈ {Y,C}} 同样两份。抄两份的后果不是难看，而是
 * 「加一个新的出站交易码 / 新钱包渠道码时改一处、漏一处」，漏掉的那一处不报错 ——
 * 表现为该笔既不算折扣也不推公交换乘，事后只能靠对账发现。</p>
 *
 * <p><b>NEVER 把 `FareCalculator` 里的 {@code "01"/"02"/"03"} 搬进本类</b>：那些是
 * {@code TRANSFER_FLAG}（01 未减免 / 02 已减免）与 {@code CUMULATIVE_TYPE}（01/02/03 累计口径），
 * 与本类的 {@code TRX_TYPE} <b>同形不同义</b>。把它们合并成一组常量，是这类收口最容易犯的错，
 * 一旦合并，日后改 {@code TRX_TYPE} 的取值会连带改掉钱包折扣的减免标记。</p>
 *
 * <p>同理 <b>NEVER</b> 把 {@code COUNTING_FLAG} 的 {@code Y/N} 与本类的
 * {@code COMPANION_FLAG} 的 {@code Y/C} 合并 —— 前者表示是否日票，后者表示同行票 / 第三方票。</p>
 */
public final class GateTxnPayFieldCode {
    /** {@code TRX_TYPE}：出站（普通）。 */
    public static final String TRX_TYPE_EXIT = "02";
    /** {@code TRX_TYPE}：出站（超时 / 特殊）。两者都进扣费链路。 */
    public static final String TRX_TYPE_EXIT_SPECIAL = "03";
    /** {@code PAYMENT_VENDOR}：地铁钱包。只有这一个渠道参与钱包累计折扣与公交换乘推送。 */
    public static final String PAYMENT_VENDOR_WALLET = "0B";
    /** {@code CHANNEL_TYPE}：蓝牙渠道，不推公交换乘。 */
    public static final String CHANNEL_TYPE_BLUETOOTH = "01";
    /** {@code COMPANION_FLAG}：同行票。 */
    public static final String COMPANION_FLAG_COMPANION = "Y";
    /** {@code COMPANION_FLAG}：第三方票。 */
    public static final String COMPANION_FLAG_THIRD_PARTY = "C";

    private GateTxnPayFieldCode() {
    }

    /**
     * 是否出站类交易（进站交易只更新票卡状态、不进扣款链路）。
     *
     * <p>判定 <b>MUST</b> 用白名单而非「非进站即出站」：报文里还会出现其它交易类型，
     * 放行等于给非出站交易也扣一次钱。</p>
     */
    public static boolean isExitTrxType(String trxType) {
        return TRX_TYPE_EXIT.equals(trxType) || TRX_TYPE_EXIT_SPECIAL.equals(trxType);
    }

    /** 是否地铁钱包渠道。大小写不敏感，与两处原实现的 {@code equalsIgnoreCase} 保持一致。 */
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
