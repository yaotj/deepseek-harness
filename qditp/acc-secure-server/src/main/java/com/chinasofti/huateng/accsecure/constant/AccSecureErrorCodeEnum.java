package com.chinasofti.huateng.accsecure.constant;

/**
 * ACC 安全服务错误码定义。
 */
public enum AccSecureErrorCodeEnum {
    SUCCESS("0000", "成功"),
    FAIL("9999", "失败"),
    SYSTEM_ERROR("9001", "系统内部错误"),
    INVALID_PARAM("8001", "无效的参数"),
    ACC_CALL_FAIL("8007", "ACC服务不可用");

    private final String code;
    private final String msg;

    AccSecureErrorCodeEnum(String code, String msg) {
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
