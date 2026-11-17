package com.chinasofti.huateng.model.enums;


public enum CardTypeCodeEnum {

    QR_POSTPAID("0441", "二维码后付费单程票"),

    HCE_POSTPAID("0442", "HCE后付费单程票"),

    NEW_HCE_POSTPAID("0443", "新版HCE后付费单程票"),

    EMPLOYEE("0444", "员工票"),

    ONE_DAY("0445", "一日票"),

    THREE_DAY("0446", "三日票"),

    SEVEN_DAY("0447", "七日票"),


    COUNTING("0448", "多日计次票"),

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


    public static boolean isHceCard(String cardType) {
        if (cardType == null) {
            return false;
        }
        String normalized = cardType.trim();
        return HCE_POSTPAID.code.equals(normalized) || NEW_HCE_POSTPAID.code.equals(normalized);
    }


    public static boolean isEmployeeCard(String cardType) {
        if (cardType == null) {
            return false;
        }
        return EMPLOYEE.code.equals(cardType.trim());
    }


    public static boolean isDailyTicket(String cardType) {
        if (cardType == null) {
            return false;
        }
        String normalized = cardType.trim();
        return ONE_DAY.code.equals(normalized) || THREE_DAY.code.equals(normalized)
                || SEVEN_DAY.code.equals(normalized) || COUNTING.code.equals(normalized);
    }


    public static boolean usesQrTicketType(String cardType) {
        if (cardType == null) {
            return false;
        }
        String normalized = cardType.trim();
        return EMPLOYEE.code.equals(normalized)
                || ONE_DAY.code.equals(normalized) || THREE_DAY.code.equals(normalized)
                || SEVEN_DAY.code.equals(normalized) || COUNTING.code.equals(normalized)
                || AI_SHAN_DONG.code.equals(normalized);
    }


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
