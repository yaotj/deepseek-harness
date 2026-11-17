package com.chinasofti.huateng.account.domain;


public enum EmployeeCardStatus {

    NORMAL(1),

    DISABLED(2),

    NOT_ENABLED(3),

    CANCELED(4);

    private final int code;

    EmployeeCardStatus(int code) {
        this.code = code;
    }


    public int code() {
        return code;
    }


    public boolean is(Integer status) {
        return status != null && status == code;
    }


    public static boolean activatableFrom(Integer status) {
        return NOT_ENABLED.is(status) || DISABLED.is(status);
    }


    public static boolean isKnownCode(Integer status) {
        if (status == null) {
            return false;
        }
        for (EmployeeCardStatus value : values()) {
            if (value.code == status) {
                return true;
            }
        }
        return false;
    }
}
