package com.chinasofti.huateng.facepay.api.device;

/**
 * TVM 对外 {@code retCode} 取值，逐字照搬旧 {@code TvmPayCodeEnum}（甲方规格表 70）。
 *
 * <p><b>注意 {@code retCode} 表示的是「请求是否被受理」，不是「支付是否成功」。</b>查询支付结果
 * 接口即使查到支付失败，{@code retCode} 仍是 {@code 0000}，业务结果只体现在响应体的
 * {@code paymentResult} 里。这个反直觉的形态是既有契约，NEVER 在重写里「修正」。</p>
 */
public enum DeviceRetCode {

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

    DeviceRetCode(String code, String msg) {
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
