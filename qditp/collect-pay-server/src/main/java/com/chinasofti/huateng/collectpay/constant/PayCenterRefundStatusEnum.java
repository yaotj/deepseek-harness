package com.chinasofti.huateng.collectpay.constant;

/** ITP支付订单状态枚举。 */
public enum PayCenterRefundStatusEnum {

    REFUND_ING("PROCESSING", "退款中"),
    REFUND_SUCCESS("SUCCESS", "退款成功"),
    REFUNDING_FAIL("FAIL", "退款失败");

    private final String code;
    private final String desc;

    PayCenterRefundStatusEnum(String code, String desc) {
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
    public static PayCenterRefundStatusEnum fromCode(String code) {
        for (PayCenterRefundStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
