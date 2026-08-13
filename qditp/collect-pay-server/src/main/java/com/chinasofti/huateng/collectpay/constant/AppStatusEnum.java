package com.chinasofti.huateng.collectpay.constant;

/**
 * ITP支付订单状态枚举。
 * 0-支付中，1-支付成功，2-支付失败，3-未支付
 */
public enum AppStatusEnum {
    REFUND_ING("PROCESSING", "退款中"),
    REFUND_SUCCESS("SUCCESS", "退款成功"),
    REFUND_FAIL("FAIL", "退款失败");

    private final String code;
    private final String desc;

    AppStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据code获取枚举。
     */
    public static AppStatusEnum fromCode(String code) {
        for (AppStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
