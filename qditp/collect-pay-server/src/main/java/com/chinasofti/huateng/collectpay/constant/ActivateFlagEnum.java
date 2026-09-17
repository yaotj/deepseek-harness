package com.chinasofti.huateng.collectpay.constant;

import org.springframework.stereotype.Component;

/** ITP支付订单状态枚举。 */
public enum ActivateFlagEnum {
    ACTIVATE_INIT("0", "未激活"),
    ACTIVATE_ED("1", "已激活");

    private final String code;
    private final String desc;

    ActivateFlagEnum(String code, String desc) {
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
    public static ActivateFlagEnum fromCode(String code) {
        for (ActivateFlagEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
