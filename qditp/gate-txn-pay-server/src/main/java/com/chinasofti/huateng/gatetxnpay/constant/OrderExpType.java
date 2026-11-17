package com.chinasofti.huateng.gatetxnpay.constant;


public enum OrderExpType {

    NORMAL("0"),
    ONE_SIDE_IN("1"),
    ONE_SIDE_OUT("2"),
    MANUAL_IN("3"),
    MANUAL_OUT("4"),
    DUAL_SEGMENT_TIMEOUT("5");

    private final String code;

    OrderExpType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public boolean is(String value) {
        return code.equals(value);
    }

    public static boolean isOneSideIn(String value) {
        return ONE_SIDE_IN.is(value);
    }

    public static boolean isOneSideOut(String value) {
        return ONE_SIDE_OUT.is(value);
    }

    public static boolean isOneSide(String value) {
        return ONE_SIDE_IN.is(value) || ONE_SIDE_OUT.is(value);
    }
}
