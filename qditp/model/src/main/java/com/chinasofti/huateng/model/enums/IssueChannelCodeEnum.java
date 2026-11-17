package com.chinasofti.huateng.model.enums;


public enum IssueChannelCodeEnum {

    NORMAL("01", "正常渠道"),

    ALIPAY("07", "支付宝"),
    ;

    private final String code;
    private final String desc;

    private static final int ALIPAY_THIRD_USER_ID_LENGTH = 10;

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


    public static boolean isAlipay(String issueChannelCode) {
        return ALIPAY.code.equals(issueChannelCode);
    }


    public static int thirdUserIdLength(String issueChannelCode) {
        return isAlipay(issueChannelCode) ? ALIPAY_THIRD_USER_ID_LENGTH : DEFAULT_THIRD_USER_ID_LENGTH;
    }
}
