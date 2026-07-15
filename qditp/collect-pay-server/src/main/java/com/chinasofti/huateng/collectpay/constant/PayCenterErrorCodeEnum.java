package com.chinasofti.huateng.collectpay.constant;

import org.apache.commons.lang3.StringUtils;

/**
 * 支付平台响应码枚举。
 * 对应支付平台公共响应码定义。
 */
public enum PayCenterErrorCodeEnum {
    SUCCESS("200", "成功"),
    FAIL("-1", "失败/系统异常"),
    INVALID_PARAM("1001", "参数校验失败"),
    MERCHANT_NOT_EXIST("1002", "商户不存在"),
    SIGN_VERIFY_FAIL("1003", "签名验证失败"),
    ORDER_NOT_EXIST("2001", "订单不存在"),
    ORDER_STATUS_ERROR("2002", "订单状态异常"),
    SIGN_NOT_EXIST("3001", "签约不存在"),
    SIGN_STATUS_ERROR("3002", "签约状态异常"),
    REFUND_ORDER_NOT_EXIST("4001", "退款单不存在"),
    REFUND_AMOUNT_EXCEED("4002", "退款金额超限");

    private final String code;
    private final String msg;

    PayCenterErrorCodeEnum(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    /**
     * 根据code获取枚举。
     */
    public static PayCenterErrorCodeEnum fromCode(String code) {
        for (PayCenterErrorCodeEnum e : values()) {
            if (StringUtils.equals(e.getCode(), code)) {
                return e;
            }
        }
        return null;
    }
}
