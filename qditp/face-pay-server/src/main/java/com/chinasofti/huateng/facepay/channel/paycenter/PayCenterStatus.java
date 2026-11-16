package com.chinasofti.huateng.facepay.channel.paycenter;

public enum PayCenterStatus {

    ORDERED("ORDERED"),
    PROCESSING("PROCESSING"),
    SUCCESS("SUCCESS"),
    FAIL("FAIL"),
    FAILED("FAILED"),
    UNPAID("UNPAID");

    private final String code;

    PayCenterStatus(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public boolean isFailed() {
        return this == FAIL || this == FAILED;
    }

    /** 非终态：支付中心已受理但用户尚未完成支付，订单仍可能继续推进。 */
    public boolean isNonTerminal() {
        return this == ORDERED || this == PROCESSING;
    }

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
