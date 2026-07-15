package com.chinasofti.huateng.collectpay.constant;

/**
 * 取票支付服务错误码定义。
 */
public enum CollectPayErrorCodeEnum {
    SUCCESS("0000", "成功"),
    FAIL("9999", "失败"),
    SYSTEM_ERROR("9001", "系统内部错误"),
    INVALID_PARAM("8001", "无效的参数"),
    ORDER_NOT_EXIST("8002", "订单不存在"),
    ORDER_STATUS_ERROR("8003", "订单状态异常"),
    PAYMENT_FAILED("8004", "支付失败"),
    PAYMENT_TIMEOUT("8005", "支付超时"),
    REFUND_FAILED("8006", "退款失败"),
    PAY_CENTER_ERROR("8007", "支付中心返回错误"),
    SIGN_ERROR("8008", "签名验证失败"),
    SERVICE_PROVIDER_UNAVAILABLE("8009", "服务提供商不可用");

    private final String code;
    private final String msg;

    CollectPayErrorCodeEnum(String code, String msg) {
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
