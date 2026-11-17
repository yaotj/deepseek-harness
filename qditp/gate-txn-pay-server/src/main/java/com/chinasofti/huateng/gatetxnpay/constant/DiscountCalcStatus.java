package com.chinasofti.huateng.gatetxnpay.constant;

public enum DiscountCalcStatus {
    SUCCESS("SUCCESS"),
    SKIPPED("SKIPPED"),
    FALLBACK("FALLBACK"),
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
