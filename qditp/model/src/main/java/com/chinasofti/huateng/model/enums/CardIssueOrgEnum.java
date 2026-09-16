package com.chinasofti.huateng.model.enums;

/**
 * 发卡机构编码枚举（APP 开户 IF8A-01 上送的 {@code cardIssueCode} 原值）。
 *
 * <p><b>两套宽度是刻意的，勿统一</b>：</p>
 * <ul>
 *   <li>本枚举的 {@code code} 是 <b>4 位</b>发卡机构码，落 {@code USER_ITP_REG_INFO.ISSUE_ORG_CODE}；</li>
 *   <li>{@link IssueChannelCodeEnum} 的 code 是 <b>2 位</b>发行渠道码，进码体的「发行渠道位」；</li>
 *   <li>{@code USER_ITP_REG_INFO.CARD_ISSUE_CODE} 存归一化后的 <b>4 位</b>渠道码（{@code 0001} / {@code 0007}），
 *       由 industry-data-server 的 {@code normalizeHex(value, 2, "01")} 取右 2 位得到码体所需的 {@code 01} / {@code 07}。</li>
 * </ul>
 *
 * <p>历史缺陷背景（2026-09-09 定位）：归一化缺失时 {@code CARD_ISSUE_CODE} 直接存 4 位机构码，
 * 被截右 2 位后 {@code 5412 -> 12}、{@code 0008 -> 08} 均落到非法渠道值，
 * 仅 {@code 0007 -> 07} 因巧合正确。`QRCODE_TXN_DETAIL` 因此在
 * 2026-08-07 ~ 修复日区间留下 82 条错误发行渠道位（历史交易不回改）。
 * 详见 {@code docs/testing/user-card/05-阻塞项与缺陷候选.md} B14。</p>
 */
public enum CardIssueOrgEnum {

    /** 0004 - 海上巴士 */
    SEA_BUS("0004", "海上巴士", IssueChannelCodeEnum.NORMAL),

    /** 0007 - 支付宝出行 */
    ALIPAY_TRIP("0007", "支付宝出行", IssueChannelCodeEnum.ALIPAY),

    /** 0008 - 成都地铁 */
    CHENGDU_METRO("0008", "成都地铁", IssueChannelCodeEnum.NORMAL),

    /** 0020 - 青岛地铁（最早一批用户，来源待业务确认） */
    QINGDAO_METRO_LEGACY("0020", "青岛地铁(早期)", IssueChannelCodeEnum.NORMAL),

    /** 5412 - 青岛地铁 */
    QINGDAO_METRO("5412", "青岛地铁", IssueChannelCodeEnum.NORMAL),

    /** 5413 - 畅行U惠小程序 */
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
     * 把 APP 上送的发卡机构码归一为 <b>4 位</b>发行渠道码，用于落 {@code CARD_ISSUE_CODE}。
     *
     * <p>未知机构码一律归 {@link #DEFAULT_ISSUE_CHANNEL_CODE_4}，<b>不抛异常、不拒绝开户</b>——
     * APP 新增渠道时先保证码体合法，异常由调用方打 ERROR 后续补字典。</p>
     */
    public static String toIssueChannelCode4(String cardIssueCode) {
        CardIssueOrgEnum org = fromCode(cardIssueCode);
        if (org == null) {
            return DEFAULT_ISSUE_CHANNEL_CODE_4;
        }
        return "00" + org.issueChannel.getCode();
    }
}
