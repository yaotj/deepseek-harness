package com.chinasofti.huateng.wallet.constant;

/**
 * 钱包服务错误码定义。
 */
public enum WalletErrorCodeEnum {
    SUCCESS("0000", "成功"),
    FAIL("9999", "失败"),
    SYSTEM_ERROR("9001", "系统内部错误"),
    INVALID_PARAM("8001", "无效的参数"),
    SERVICE_PROVIDER_UNAVAILABLE("8007", "服务提供商不可用"),
    USER_NOT_SIGNED("8011", "用户未签约");

    private final String code;
    private final String msg;

    WalletErrorCodeEnum(String code, String msg) {
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
