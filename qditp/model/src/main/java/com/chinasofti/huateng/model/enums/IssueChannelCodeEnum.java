package com.chinasofti.huateng.model.enums;

/**
 * 发行渠道编码枚举。
 * <p>对应票务系统 issueChannelCode 字段定义。</p>
 */
public enum IssueChannelCodeEnum {

    /** 01 - 正常渠道（闸机/APP 直接交易） */
    NORMAL("01", "正常渠道"),

    /** 07 - 支付宝渠道 */
    ALIPAY("07", "支付宝"),
    ;

    private final String code;
    private final String desc;

    IssueChannelCodeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    /**
     * 根据编码获取枚举，未知编码返回 null。
     */
    public static IssueChannelCodeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (IssueChannelCodeEnum e : values()) {
            if (e.code.equals(code.trim())) {
                return e;
            }
        }
        return null;
    }

    /**
     * 判断是否为支付宝渠道。
     */
    public static boolean isAlipay(String issueChannelCode) {
        return ALIPAY.code.equals(issueChannelCode);
    }
}
