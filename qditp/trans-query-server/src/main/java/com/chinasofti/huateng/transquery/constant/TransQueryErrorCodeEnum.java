package com.chinasofti.huateng.transquery.constant;

/** 交易查询服务错误码。 */
public enum TransQueryErrorCodeEnum {
    SUCCESS("0000", "成功"),
    INVALID_PARAM("8001", "请求参数验证失败"),
    NO_DATA("8002", "无数据"),
    SYSTEM_ERROR("9001", "系统内部错误");

    private final String code;
    private final String msg;

    TransQueryErrorCodeEnum(String code, String msg) {
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
