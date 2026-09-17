package com.chinasofti.huateng.model.enums;

/**
 * 发卡机构编码枚举（APP 开户 IF8A-01 上送的 {@code cardIssueCode} 原值）。
 */
public enum CardIssueOrgEnum {

    /** 0004 - 海上巴士。 */
    SEA_BUS("0004", "海上巴士", IssueChannelCodeEnum.NORMAL),

    /** 0007 - 支付宝出行。 */
    ALIPAY_TRIP("0007", "支付宝出行", IssueChannelCodeEnum.ALIPAY),

    /** 0008 - 成都地铁。 */
    CHENGDU_METRO("0008", "成都地铁", IssueChannelCodeEnum.NORMAL),

    /** 0020 - 青岛地铁（最早一批用户，来源待业务确认） */
    QINGDAO_METRO_LEGACY("0020", "青岛地铁(早期)", IssueChannelCodeEnum.NORMAL),

    /** 5412 - 青岛地铁。 */
    QINGDAO_METRO("5412", "青岛地铁", IssueChannelCodeEnum.NORMAL),

    /** 5413 - 畅行U惠小程序。 */
    CXUH_MINI_APP("5413", "畅行U惠小程序", IssueChannelCodeEnum.NORMAL),
    ;

    /** 未知机构码时归一到的兜底发行渠道（4 位形态）。 */
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

    /**
     * 根据 4 位机构码取枚举，未知返回 null。
     */
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

    /**
     * 把 APP 上送的发卡机构码归一为 4 位发行渠道码，用于落 {@code CARD_ISSUE_CODE}。
     */
    public static String toIssueChannelCode4(String cardIssueCode) {
        CardIssueOrgEnum org = fromCode(cardIssueCode);
        if (org == null) {
            return DEFAULT_ISSUE_CHANNEL_CODE_4;
        }
        return "00" + org.issueChannel.getCode();
    }
}
