package com.chinasofti.huateng.transquery.constant;

/**
 * 交易查询服务错误码。
 *
 * <p>取值**逐条对齐** ticket-server 的 {@code TransQueryErrorCodeEnum}：这五个接口的应答码是
 * APP 已在用的对外契约，搬迁只换实现位置、NEVER 换码值。本枚举只收本服务真正用到的那几项，
 * 不整份复制乘车码状态机的码表。
 */
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
