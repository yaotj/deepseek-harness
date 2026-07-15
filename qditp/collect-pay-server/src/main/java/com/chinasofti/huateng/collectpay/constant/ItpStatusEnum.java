package com.chinasofti.huateng.collectpay.constant;

/**
 * ITP支付订单状态枚举。
 * 0-支付中，1-支付成功，2-支付失败，3-未支付
 */
public enum ItpStatusEnum {
    PAYING("0", "支付中"),
    SUCCESS("1", "支付成功"),
    FAILED("2", "支付失败"),
    UNPAID("3", "未支付"),

    REFUND_ING("0", "退款中"),
    REFUND_SUCCESS("1", "退款成功"),
    REFUNDING_FAIL("2", "退款失败");

    private final String code;
    private final String desc;

    ItpStatusEnum(String code, String desc) {
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
    public static ItpStatusEnum fromCode(String code) {
        for (ItpStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
