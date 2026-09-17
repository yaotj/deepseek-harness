package com.chinasofti.huateng.model.ticket.enums;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * IF5A 建议操作（{@code adviceOpt}）字典。
 */
public enum AdviceOptEnum {

    /** 000 - 无需操作。 */
    NONE("000", "无需操作"),

    /** 005 - 20 分钟内免费更新（补出站，不计票价） */
    FREE_UPDATE("005", "免费更新"),

    /** 006 - 付费更新（补出站，按票价扣费） */
    PAID_UPDATE("006", "付费更新"),

    /** 018 - 补进站。 */
    SUPPLEMENT_ENTRY("018", "补进站"),

    /**
     * 020 - 免费更新（厂家字典 {@code CardAdviceOpt.FREE_UPDATE}）。
     */
    FREE_UPDATE_020("020", "免费更新");

    private final String code;
    private final String desc;

    AdviceOptEnum(String code, String desc) {
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
     * 宽松解析：无法识别时返回 null 而不抛异常。
     */
    public static AdviceOptEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AdviceOptEnum opt : values()) {
            if (opt.code.equals(code)) {
                return opt;
            }
        }
        return null;
    }

    /** 码值是否等于本枚举，供仍以 String 比较的调用点使用。 */
    public boolean matches(String code) {
        return this.code.equals(code);
    }

    /** 单元素列表，IF5A-01 的 {@code adviceOpt} 响应字段是 List。 */
    public List<String> asSingletonList() {
        return Collections.singletonList(code);
    }

    public boolean isSupplementEntry() {
        return this == SUPPLEMENT_ENTRY || this == FREE_UPDATE_020;
    }

    public boolean isSupplementExit() {
        return this == FREE_UPDATE || this == PAID_UPDATE;
    }

    /**
     * 「BOM 已完成补出站」的码值集合，供只有字符串在手的调用点直接判定。
     */
    public static final Set<String> SUPPLEMENT_EXIT_CODES =
            Set.of(FREE_UPDATE.code, PAID_UPDATE.code);
}
