package com.chinasofti.huateng.model.ticket.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;


public enum QRCodeStatusEnum {

    NO_TXN("01", "无交易"),

    END_TRIP("02", "结束行程"),

    SJT_ISSUE("03", "初始化"),

    ENTRY("04", "已进站"),

    EXIT("05", "已出站"),

    EXIT_OVERTIME("06", "超时出站"),

    UPDATE_FREE("08", "20分钟内免费更新"),

    UPDATE_PAY("09", "20分钟内付费更新"),

    UPDATE_ENTRY("10", "入站码更新"),

    ABNORMAL("70", "异常"),

    SELF_SERVICE_EXIT("80", "APP自助补出站更新"),

    SELF_SERVICE_ENTRY("81", "APP自助补进站更新"),

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


    public boolean isClosedLoop() {
        return this == END_TRIP || this == EXIT || this == EXIT_OVERTIME || this == SELF_SERVICE_EXIT;
    }


    public boolean isOpenLoop() {
        return this == ENTRY || this == SELF_SERVICE_ENTRY;
    }


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


    private static final Set<QRCodeStatusEnum> ALWAYS_ALLOWED_TARGETS = EnumSet.of(ABNORMAL, SJT_ISSUE);


    public boolean canTransitTo(QRCodeStatusEnum target) {
        if (target == null) {
            return false;
        }
        if (this == target || ALWAYS_ALLOWED_TARGETS.contains(target)) {
            return true;
        }
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }


    public static QRCodeStatusEnum parseOrNull(String code) {
        return fromCode(code);
    }
}
