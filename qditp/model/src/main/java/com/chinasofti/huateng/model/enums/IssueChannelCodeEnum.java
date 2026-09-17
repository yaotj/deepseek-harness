package com.chinasofti.huateng.model.enums;

/**
 * 发行渠道编码枚举。
 */
public enum IssueChannelCodeEnum {

    /** 01 - 正常渠道（闸机/APP 直接交易） */
    NORMAL("01", "正常渠道"),

    /** 07 - 支付宝渠道。 */
    ALIPAY("07", "支付宝"),
    ;

    private final String code;
    private final String desc;

    /** 支付宝渠道的第三方用户号定长位数。 */
    private static final int ALIPAY_THIRD_USER_ID_LENGTH = 10;

    /** 其余渠道（含未知码）的第三方用户号定长位数。 */
    private static final int DEFAULT_THIRD_USER_ID_LENGTH = 8;

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

    /**
     * 该渠道要求的「第三方用户号」定长位数：支付宝 10 位，其余 8 位。
     */
    public static int thirdUserIdLength(String issueChannelCode) {
        return isAlipay(issueChannelCode) ? ALIPAY_THIRD_USER_ID_LENGTH : DEFAULT_THIRD_USER_ID_LENGTH;
    }
}
