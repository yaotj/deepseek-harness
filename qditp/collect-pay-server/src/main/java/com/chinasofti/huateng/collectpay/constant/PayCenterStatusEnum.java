package com.chinasofti.huateng.collectpay.constant;

/**
 * 支付中心订单状态枚举。
 * 对应支付中心返回的status字段。
 */
public enum PayCenterStatusEnum {
    ORDERED("ORDERED", "已下单"),
    SUCCESS("SUCCESS", "支付成功"),
    FAILED("FAILED", "支付失败"),
    UNPAID("UNPAID", "未支付");

    private final String code;
    private final String desc;

    PayCenterStatusEnum(String code, String desc) {
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
    public static PayCenterStatusEnum fromCode(String code) {
        for (PayCenterStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
