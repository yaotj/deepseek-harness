package com.chinasofti.huateng.facepay.api.device;

/** 对外 {@code paymentResult} 取值，逐字照搬旧 {@code DevicePayCodeEnum}。 */
public enum PaymentResult {

    ORDERED("ORDERED", "已下单"),
    PROCESSING("PROCESSING", "处理中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;

    private final String msg;

    PaymentResult(String code, String msg) {
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
