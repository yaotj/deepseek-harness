package com.chinasofti.huateng.model.enums;


public enum CardIssueOrgEnum {

    SEA_BUS("0004", "海上巴士", IssueChannelCodeEnum.NORMAL),

    ALIPAY_TRIP("0007", "支付宝出行", IssueChannelCodeEnum.ALIPAY),

    CHENGDU_METRO("0008", "成都地铁", IssueChannelCodeEnum.NORMAL),

    QINGDAO_METRO_LEGACY("0020", "青岛地铁(早期)", IssueChannelCodeEnum.NORMAL),

    QINGDAO_METRO("5412", "青岛地铁", IssueChannelCodeEnum.NORMAL),

    CXUH_MINI_APP("5413", "畅行U惠小程序", IssueChannelCodeEnum.NORMAL),
    ;

    public static final String DEFAULT_ISSUE_CHANNEL_CODE_4 = "0001";

    private final String code;
    private final String desc;
    private final IssueChannelCodeEnum issueChannel;

    CardIssueOrgEnum(String code, String desc, IssueChannelCodeEnum issueChannel) {
        this.code = code;
        this.desc = desc;
        this.issueChannel = issueChannel;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public IssueChannelCodeEnum getIssueChannel() {
        return issueChannel;
    }


    public static CardIssueOrgEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        for (CardIssueOrgEnum e : values()) {
            if (e.code.equals(trimmed)) {
                return e;
            }
        }
        return null;
    }


    public static String toIssueChannelCode4(String cardIssueCode) {
        CardIssueOrgEnum org = fromCode(cardIssueCode);
        if (org == null) {
            return DEFAULT_ISSUE_CHANNEL_CODE_4;
        }
        return "00" + org.issueChannel.getCode();
    }
}
