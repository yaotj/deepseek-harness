package com.chinasofti.huateng.collectpay.constant;

/**
 * TVM扫码购票业务错误码定义。
 * 对应文档表70错误代码列表。
 */
public enum TvmPayCodeEnum {
    SUCCESS("0000", "成功"),
    FAIL("2999", "失败"),
    INVALID_DEVICE("2001", "非法设备"),
    INVALID_PARAM("2002", "非法参数"),
    NO_ACTIVE_ORDER("2003", "无激活的订单"),
    RECHARGE_AMOUNT_EXCEED("2004", "充值金额超限"),
    ORDER_NOT_PAID("2005", "订单未支付"),
    ORDER_NO_ERROR("2006", "订单号错误"),
    ORDER_REFUNDED("2007", "订单已退款"),
    ORDER_LOCKED("2008", "订单已锁定");

    private final String code;
    private final String msg;

    TvmPayCodeEnum(String code, String msg) {
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
