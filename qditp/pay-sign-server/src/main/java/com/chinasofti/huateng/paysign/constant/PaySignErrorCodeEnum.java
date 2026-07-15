package com.chinasofti.huateng.paysign.constant;

public enum PaySignErrorCodeEnum {
    SUCCESS("0000", "成功"),
    FAIL("9999", "失败"),
    SYSTEM_ERROR("9001", "系统内部错误"),
    INVALID_PARAM("8001", "无效的参数"),
    SERVICE_PROVIDER_UNAVAILABLE("8007", "支付系统不可用"),
    USER_NOT_SIGNED("8011", "用户未签约"),
    RECORD_NOT_EXIST("8012", "签约记录不存在"),
    ALREADY_SIGNED("8013", "该用户已签约此支付渠道"),
    INVALID_SIGN_DATA("8014", "无效的签约数据"),
    ORDER_CANNOT_REFUND("8180", "该订单不能退款"),
    ORDER_ALREADY_PAID("8181", "订单已支付成功，请勿重复支付"),
    ORDER_CLOSED("8182", "订单已关闭，请重新创建订单"),
    ORDER_CANNOT_ACTIVATE("8183", "订单无法激活"),
    ORDER_ALREADY_ACTIVATED("8184", "订单已激活");

    private final String code;
    private final String msg;

    PaySignErrorCodeEnum(String code, String msg) {
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
