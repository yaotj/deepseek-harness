package com.chinasofti.huateng.collectpay.constant;

public enum BomBusinessCodeEnum {
    SALE("01", "充值"),
    TOPUP("22", "充值");

    private final String code;
    private final String msg;

    BomBusinessCodeEnum(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}