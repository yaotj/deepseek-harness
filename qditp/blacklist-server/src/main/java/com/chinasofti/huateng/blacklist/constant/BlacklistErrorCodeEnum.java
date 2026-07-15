package com.chinasofti.huateng.blacklist.constant;

/**
 * blacklist-server 错误码枚举。
 */
public enum BlacklistErrorCodeEnum {
    SUCCESS("0000", "成功"),
    FAIL("9999", "失败"),
    SYSTEM_ERROR("9001", "系统内部错误"),
    INVALID_PARAM("8001", "无效的参数");

    private final String code;
    private final String msg;

    BlacklistErrorCodeEnum(String code, String msg) {
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
