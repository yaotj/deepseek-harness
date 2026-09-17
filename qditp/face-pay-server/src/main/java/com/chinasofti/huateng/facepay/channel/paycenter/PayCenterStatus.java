package com.chinasofti.huateng.facepay.channel.paycenter;

/** 支付中心 {@code data.status} 取值。 */
public enum PayCenterStatus {

    ORDERED("ORDERED"),
    SUCCESS("SUCCESS"),
    /** 支付中心实际下发的失败取值。 */
    FAIL("FAIL"),
    /** 文档未列、实测未见的失败取值，保留兜底，NEVER 单独判它。 */
    FAILED("FAILED"),
    UNPAID("UNPAID");

    private final String code;

    PayCenterStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /** 支付失败。 */
    public boolean isFailed() {
        return this == FAIL || this == FAILED;
    }

    /** 未知取值返回 {@code null}，由调用方按 UNKNOWN 处理。 */
    public static PayCenterStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PayCenterStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }
}
