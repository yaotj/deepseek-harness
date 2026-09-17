package com.chinasofti.huateng.collectpay.constant;

/** ITP支付订单状态枚举。 */
public enum AppStatusEnum {

    PAY_SUCCESS("SUCCESS", "支付成功"),
    PAY_FAIL("FAIL", "支付失败"),

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

    /** 根据code获取枚举。 */
    public static AppStatusEnum fromCode(String code) {
        for (AppStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
