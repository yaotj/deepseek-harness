package com.chinasofti.huateng.gatetxnpay.constant;

public enum DebitStatus {
    INIT("INIT"),
    PROCESSING("PROCESSING"),
    RETRY("RETRY"),
    SUCCESS("SUCCESS"),
    FAIL("FAIL");

    private final String code;

    DebitStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean is(String value) {
        return code.equals(value);
    }

    public static boolean isRetryable(String value) {
        return RETRY.is(value) || INIT.is(value);
    }


    public static boolean isBatchRetryable(String value) {
        return RETRY.is(value) || FAIL.is(value);
    }

    public static boolean isRefundable(String value) {
        return SUCCESS.is(value) || PROCESSING.is(value);
    }
}
