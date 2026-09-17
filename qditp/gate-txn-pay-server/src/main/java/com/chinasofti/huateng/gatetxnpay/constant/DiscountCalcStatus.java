package com.chinasofti.huateng.gatetxnpay.constant;

/** {@code GATE_TXN_PAY.DISCOUNT_CALC_STATUS} 的唯一取值来源。 */
public enum DiscountCalcStatus {
    /** 优惠算好了。 */
    SUCCESS("SUCCESS"),
    /** 本笔不适用优惠（非钱包渠道 / 同行票 / 免扣费等），不是失败。 */
    SKIPPED("SKIPPED"),
    /** 取数或算价失败，已退化为不打折的原价，交易照常放行。 */
    FALLBACK("FALLBACK"),
    /** 离线码入库时拿不到金额，落痕等 {@code OfflineFareRecoveryProcessor} 重算。 */
    OFFLINE_FARE_PENDING("OFFLINE_FARE_PENDING");

    private final String code;

    DiscountCalcStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean is(String value) {
        return code.equals(value);
    }
}
