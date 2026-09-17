package com.chinasofti.huateng.collectpay.constant;

/** TVM扫码购票业务错误码定义。 */
public enum AppCodeEnum {
    SUCCESS("0000", "成功"),
//    FAIL("2999", "失败"),
    FAIL("9999", "失败");


    private final String code;
    private final String msg;

    AppCodeEnum(String code, String msg) {
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
