package com.chinasofti.huateng.model.enums;

/**
 * 票卡类型编码枚举。
 * <p>对应 AlipayTripPushTransDataReqDTO 注释中的卡类型定义。</p>
 *
 * @see com.chinasofti.huateng.model.app.CardTypeMapping
 */
public enum CardTypeCodeEnum {

    /** 二维码后付费单程票 */
    QR_POSTPAID("0441", "二维码后付费单程票"),

    /** HCE 后付费单程票 */
    HCE_POSTPAID("0442", "HCE后付费单程票"),

    /** 新版 HCE 后付费单程票 */
    NEW_HCE_POSTPAID("0443", "新版HCE后付费单程票"),

    /** 员工票（免费乘车） */
    EMPLOYEE("0444", "员工票"),

    /** 一日票 */
    ONE_DAY("0445", "一日票"),

    /** 三日票 */
    THREE_DAY("0446", "三日票"),

    /** 七日票 */
    SEVEN_DAY("0447", "七日票"),

    /** 月票 */
    MONTHLY("0448", "月票"),

    /** 爱山东 */
    AI_SHAN_DONG("044A", "爱山东"),
    ;

    private final String code;
    private final String desc;

    CardTypeCodeEnum(String code, String desc) {
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
     * 判断是否为 HCE 卡类型（0442 / 0443）。
     */
    public static boolean isHceCard(String cardType) {
        if (cardType == null) {
            return false;
        }
        String normalized = cardType.trim();
        return HCE_POSTPAID.code.equals(normalized) || NEW_HCE_POSTPAID.code.equals(normalized);
    }

    /**
     * 判断是否为员工票（0444）。
     */
    public static boolean isEmployeeCard(String cardType) {
        if (cardType == null) {
            return false;
        }
        return EMPLOYEE.code.equals(cardType.trim());
    }

    /**
     * 判断是否为日票类型（0445 / 0446 / 0447 / 0448）。
     */
    public static boolean isDailyTicket(String cardType) {
        if (cardType == null) {
            return false;
        }
        String normalized = cardType.trim();
        return ONE_DAY.code.equals(normalized) || THREE_DAY.code.equals(normalized)
                || SEVEN_DAY.code.equals(normalized) || MONTHLY.code.equals(normalized);
    }

    /**
     * 判断是否需要映射为二维码行业码体（员工票及日票的行业数据统一使用 0441）。
     */
    public static boolean usesQrTicketType(String cardType) {
        if (cardType == null) {
            return false;
        }
        String normalized = cardType.trim();
        return EMPLOYEE.code.equals(normalized)
                || ONE_DAY.code.equals(normalized) || THREE_DAY.code.equals(normalized)
                || SEVEN_DAY.code.equals(normalized) || MONTHLY.code.equals(normalized)
                || AI_SHAN_DONG.code.equals(normalized);
    }

    /**
     * 根据编码获取枚举，未知编码返回 null。
     */
    public static CardTypeCodeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CardTypeCodeEnum e : values()) {
            if (e.code.equals(code.trim())) {
                return e;
            }
        }
        return null;
    }
}
