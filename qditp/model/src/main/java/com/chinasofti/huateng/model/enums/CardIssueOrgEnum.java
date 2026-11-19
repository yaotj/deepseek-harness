package com.chinasofti.huateng.model.enums;


/**
 * 发卡机构代码枚举
 * 保留已知发卡机构作为参考，但不再做强限制
 * 支持任意4位数字的发卡机构代码，未登记的代码使用默认渠道
 */
public enum CardIssueOrgEnum {

    SEA_BUS("0004", "海上巴士", IssueChannelCodeEnum.NORMAL),

    ALIPAY_TRIP("0007", "支付宝出行", IssueChannelCodeEnum.ALIPAY),

    CHENGDU_METRO("0008", "成都地铁", IssueChannelCodeEnum.NORMAL),

    QINGDAO_METRO_LEGACY("0020", "青岛地铁(早期)", IssueChannelCodeEnum.NORMAL),

    QINGDAO_METRO("5412", "青岛地铁", IssueChannelCodeEnum.NORMAL),

    CXUH_MINI_APP("5413", "畅行U惠小程序", IssueChannelCodeEnum.NORMAL),

    DIGITAL_CURRENCY("0009", "数币乘车码", IssueChannelCodeEnum.NORMAL),
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


    /**
     * 从代码获取发卡机构枚举
     * 如果代码未登记，返回 null（调用方需要处理 null 情况）
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
     * 检查发卡机构代码是否已登记
     */
    public static boolean isRegistered(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取发卡机构代码的描述
     * 如果未登记，返回"未知发卡机构"
     */
    public static String getDesc(String code) {
        CardIssueOrgEnum org = fromCode(code);
        return org != null ? org.getDesc() : "未知发卡机构";
    }


    /**
     * 将发卡机构代码转换为发行渠道代码（4位）
     * 未登记的发卡机构使用默认渠道 NORMAL("01")
     */
    public static String toIssueChannelCode4(String cardIssueCode) {
        CardIssueOrgEnum org = fromCode(cardIssueCode);
        if (org == null) {
            // 未登记的发卡机构使用默认渠道
            return DEFAULT_ISSUE_CHANNEL_CODE_4;
        }
        return "00" + org.issueChannel.getCode();
    }
}
