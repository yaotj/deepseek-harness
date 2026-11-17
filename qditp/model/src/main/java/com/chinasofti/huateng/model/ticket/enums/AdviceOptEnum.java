package com.chinasofti.huateng.model.ticket.enums;

import java.util.Collections;
import java.util.List;
import java.util.Set;


public enum AdviceOptEnum {

    NONE("000", "无需操作"),

    FREE_UPDATE("005", "免费更新"),

    PAID_UPDATE("006", "付费更新"),

    SUPPLEMENT_ENTRY("018", "补进站"),


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

    public boolean matches(String code) {
        return this.code.equals(code);
    }

    public List<String> asSingletonList() {
        return Collections.singletonList(code);
    }

    public boolean isSupplementEntry() {
        return this == SUPPLEMENT_ENTRY;
    }

    public boolean isSupplementExit() {
        return this == FREE_UPDATE || this == PAID_UPDATE || this == FREE_UPDATE_020;
    }


    public static final Set<String> SUPPLEMENT_EXIT_CODES =
            Set.of(FREE_UPDATE.code, PAID_UPDATE.code, FREE_UPDATE_020.code);
}
