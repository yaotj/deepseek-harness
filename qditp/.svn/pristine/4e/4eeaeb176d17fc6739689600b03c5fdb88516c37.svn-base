package com.chinasofti.huateng.collectpay.constant;

public enum BomPayCodeEnum {
    SUCCESS("0000", "成功"),
    PASSENGER_CANCEL("0001", "乘客取消订单"),
    FAIL("8999", "失败"),
    INVALID_DEVICE("8001", "非法设备"),
    ORDER_UNPAID("8002", "订单未支付"),
    INVALID_PARAM("8003", "非法参数"),
    NO_ACTIVE_ORDER("8004", "无激活的订单"),
    TOPUP_AMOUNT_EXCEED("8005", "充值金额超限"),
    ORDER_NO_ERROR("8006", "订单号错误"),
    ORDER_REFUNDED("8007", "订单已退款");

    private final String code;
    private final String msg;

    BomPayCodeEnum(String code, String msg) {
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