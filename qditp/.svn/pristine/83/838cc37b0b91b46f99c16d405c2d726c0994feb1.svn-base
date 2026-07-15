package com.chinasofti.huateng.collectticket.constant;

/**
 * 取票服务错误码定义。
 */
public enum CollectTicketErrorCodeEnum {
    SUCCESS("0000", "成功"),
    FAIL("9999", "失败"),
    SYSTEM_ERROR("9001", "系统内部错误"),
    INVALID_PARAM("8001", "无效的参数"),
    ORDER_NOT_EXIST("8002", "订单不存在"),
    ORDER_STATUS_ERROR("8003", "订单状态异常"),
    COLLECT_NOT_ALLOWED("8004", "不允许取票"),
    COLLECT_ALREADY_DONE("8005", "已取票完成"),
    TICKET_EXPIRED("8006", "二维码已过期"),
    DEVICE_NOT_EXIST("8007", "设备不存在"),
    SERVICE_PROVIDER_UNAVAILABLE("8008", "服务提供商不可用"),
    SIGN_ERROR("8009", "签名验证失败");

    private final String code;
    private final String msg;

    CollectTicketErrorCodeEnum(String code, String msg) {
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
