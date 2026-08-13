package com.chinasofti.huateng.collectpay.constant;

/**
 * ITP支付订单状态枚举。
 * 0-支付中，1-支付成功，2-支付失败，3-未支付
 */
public enum BusinessTypeEnum {
    TVM_SCAN_QR_BUYTICKET("01", "扫码购票"),
    TVM_SCAN_QR_RECHARGE("02", "扫码充值"),
    TVM_SCAN_QR_TAKETICKET("03", "扫码取票"),
    BOM_SCANED_PAY("04", "bom支付");

    private final String code;
    private final String desc;

    BusinessTypeEnum(String code, String desc) {
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
    public static BusinessTypeEnum fromCode(String code) {
        for (BusinessTypeEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}
