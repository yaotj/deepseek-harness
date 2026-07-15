package com.chinasofti.huateng.online.constant;

public enum OnlineErrorCodeEnum {
    SUCCESS("0000", "成功"),
    FAILED("2999", "失败"),
    SYSTEM_ERROR("9999", "系统内部错误"),
    INVALID_PARAM("2002", "非法参数"),
    INVALID_DEVICE("2001", "非法设备"),
    NO_ACTIVE_ORDER("2003", "无激活的订单"),
    TOPUP_AMOUNT_EXCEED("2004", "充值金额超限"),
    ORDER_NOT_PAID("2005", "订单未支付"),
    ORDER_NOT_FOUND("2006", "订单号错误"),
    ORDER_REFUNDED("2007", "订单已退款"),
    ORDER_LOCKED("2008", "订单已锁定"),
    CARD_NOT_FOUND("3001", "未查到卡信息"),
    BOM_PROCESSING("8300", "BOM处理交易，请联系客服"),
    CODE_STATUS_NORMAL("8301", "码状态正常，无需更新"),
    EXIT_STATION_SUCCESS("8302", "补出站成功"),
    ENTRY_STATION_SUCCESS("8303", "补进站成功"),
    EXCEED_UPGRADE_LIMIT("8304", "无法自助补进出站，超过限制次数"),
    BOM_RETURN_STATUS_ABNORMAL1("8311", "BOM返回值状态异常"),
    BOM_RETURN_STATUS_ABNORMAL2("8312", "BOM返回值状态异常");

    private final String code;
    private final String msg;

    OnlineErrorCodeEnum(String code, String msg) {
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
