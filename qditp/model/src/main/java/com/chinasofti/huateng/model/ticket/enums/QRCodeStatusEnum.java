package com.chinasofti.huateng.model.ticket.enums;

/**
 * QR码车票状态枚举。
 * <p>对应 QRCODE_STATUS 表的 codeStatus 字段</p>
 */
public enum QRCodeStatusEnum {

    /** 01 - 无交易 */
    NO_TXN("01", "无交易"),

    /** 02 - 结束行程 */
    END_TRIP("02", "结束行程"),

    /** 03 - 初始化 */
    SJT_ISSUE("03", "初始化"),

    /** 04 - 已进站 */
    ENTRY("04", "已进站"),

    /** 05 - 已出站 */
    EXIT("05", "已出站"),

    /** 06 - 超时出站 */
    EXIT_OVERTIME("06", "超时出站"),

    /** 08 - 20分钟内免费更新（BOM/PCA非付费区） */
    UPDATE_FREE("08", "20分钟内免费更新"),

    /** 09 - 20分钟内付费更新（BOM/PCA非付费区） */
    UPDATE_PAY("09", "20分钟内付费更新"),

    /** 10 - 入站码更新 */
    UPDATE_ENTRY("10", "入站码更新"),

    /** 70 - 异常 */
    ABNORMAL("70", "异常"),

    /** 80 - APP自助补出站更新 */
    SELF_SERVICE_EXIT("80", "APP自助补出站更新"),

    /** 81 - APP自助补进站更新 */
    SELF_SERVICE_ENTRY("81", "APP自助补进站更新"),

    /** FF - 进站失败 */
    ENTRY_FAIL("FF", "进站失败");

    private final String code;
    private final String desc;

    QRCodeStatusEnum(String code, String desc) {
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
     * 根据编码获取QR码状态枚举。
     */
    public static QRCodeStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (QRCodeStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否为闭环状态（02/05/06/80）。
     */
    public boolean isClosedLoop() {
        return this == END_TRIP || this == EXIT || this == EXIT_OVERTIME || this == SELF_SERVICE_EXIT;
    }

    /**
     * 判断是否为开环状态（04/81）。
     */
    public boolean isOpenLoop() {
        return this == ENTRY || this == SELF_SERVICE_ENTRY;
    }
}
