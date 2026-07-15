package com.chinasofti.huateng.key.constant;

/**
 * key-server 错误码枚举。
 */
public enum KeyErrorCodeEnum {
    SUCCESS("0000", "成功"),
    FAIL("9999", "失败"),
    SYSTEM_ERROR("9001", "系统内部错误"),
    INVALID_PARAM("8001", "无效的参数"),
    NO_CA_KEYSTORE("8005", "无可用CA证书"),
    INVALID_PUBLIC_KEY("8006", "用户公钥格式错误");

    private final String code;
    private final String msg;

    KeyErrorCodeEnum(String code, String msg) {
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
