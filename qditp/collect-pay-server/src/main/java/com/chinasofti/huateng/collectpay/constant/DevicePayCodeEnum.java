package com.chinasofti.huateng.collectpay.constant;

/**
 * TVM扫码购票业务错误码定义。
 * 对应文档表70错误代码列表。
 */
public enum DevicePayCodeEnum {
    ORDERED("ORDERED", "已下单"),
    SUCCESS("SUCCESS", "成功"),

    FAILED("FAILED", "失败");

    private final String code;
    private final String msg;

    DevicePayCodeEnum(String code, String msg) {
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
