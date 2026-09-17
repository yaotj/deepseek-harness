package com.chinasofti.huateng.model.ticket.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * QR码车票状态枚举。
 */
public enum QRCodeStatusEnum {

    /** 01 - 无交易。 */
    NO_TXN("01", "无交易"),

    /** 02 - 结束行程。 */
    END_TRIP("02", "结束行程"),

    /** 03 - 初始化。 */
    SJT_ISSUE("03", "初始化"),

    /** 04 - 已进站。 */
    ENTRY("04", "已进站"),

    /** 05 - 已出站。 */
    EXIT("05", "已出站"),

    /** 06 - 超时出站。 */
    EXIT_OVERTIME("06", "超时出站"),

    /** 08 - 20分钟内免费更新（BOM/PCA非付费区） */
    UPDATE_FREE("08", "20分钟内免费更新"),

    /** 09 - 20分钟内付费更新（BOM/PCA非付费区） */
    UPDATE_PAY("09", "20分钟内付费更新"),

    /** 10 - 入站码更新。 */
    UPDATE_ENTRY("10", "入站码更新"),

    /** 70 - 异常。 */
    ABNORMAL("70", "异常"),

    /** 80 - APP自助补出站更新。 */
    SELF_SERVICE_EXIT("80", "APP自助补出站更新"),

    /** 81 - APP自助补进站更新。 */
    SELF_SERVICE_ENTRY("81", "APP自助补进站更新"),

    /** FF - 进站失败。 */
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

    /**
     * 迁移白名单。
     */
    private static final Map<QRCodeStatusEnum, Set<QRCodeStatusEnum>> ALLOWED = Map.ofEntries(
            Map.entry(NO_TXN, EnumSet.of(ENTRY)),
            Map.entry(SJT_ISSUE, EnumSet.of(ENTRY, ENTRY_FAIL, SELF_SERVICE_ENTRY)),
            Map.entry(ENTRY, EnumSet.of(EXIT, EXIT_OVERTIME, UPDATE_FREE, UPDATE_PAY, SELF_SERVICE_EXIT)),
            Map.entry(EXIT, EnumSet.of(ENTRY, ENTRY_FAIL, SELF_SERVICE_ENTRY)),
            Map.entry(EXIT_OVERTIME, EnumSet.of(ENTRY, ENTRY_FAIL, SELF_SERVICE_ENTRY)),
            Map.entry(END_TRIP, EnumSet.of(ENTRY, SELF_SERVICE_ENTRY)),
            Map.entry(UPDATE_FREE, EnumSet.of(ENTRY, EXIT, EXIT_OVERTIME, SELF_SERVICE_ENTRY)),
            Map.entry(UPDATE_PAY, EnumSet.of(ENTRY, EXIT, EXIT_OVERTIME, SELF_SERVICE_ENTRY)),
            Map.entry(UPDATE_ENTRY, EnumSet.of(EXIT, EXIT_OVERTIME, UPDATE_FREE, SELF_SERVICE_ENTRY)),
            Map.entry(SELF_SERVICE_EXIT, EnumSet.of(ENTRY, ENTRY_FAIL, SELF_SERVICE_ENTRY)),
            Map.entry(SELF_SERVICE_ENTRY, EnumSet.of(EXIT, EXIT_OVERTIME, UPDATE_PAY, SELF_SERVICE_EXIT, ENTRY_FAIL)),
            Map.entry(ENTRY_FAIL, EnumSet.of(ENTRY)),
            Map.entry(ABNORMAL, EnumSet.noneOf(QRCodeStatusEnum.class)));

    /**
     * 无论当前状态为何都允许落入的目标态。
     */
    private static final Set<QRCodeStatusEnum> ALWAYS_ALLOWED_TARGETS = EnumSet.of(ABNORMAL, SJT_ISSUE);

    /**
     * 只做快速判定与告警提示。
     */
    public boolean canTransitTo(QRCodeStatusEnum target) {
        if (target == null) {
            return false;
        }
        if (this == target || ALWAYS_ALLOWED_TARGETS.contains(target)) {
            return true;
        }
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * 宽松解析：无法识别时返回 null 而不抛异常。
     */
    public static QRCodeStatusEnum parseOrNull(String code) {
        return fromCode(code);
    }
}
