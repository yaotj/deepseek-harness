package com.chinasofti.huateng.model.enums;


public enum TrxTypeCodeEnum {

    ENTRY("01", "进站"),

    EXIT("02", "出站"),

    EXIT_OVERTIME("03", "超时出站"),

    ENTRY_FAIL("04", "进站失败"),

    ABNORMAL("99", "异常"),
    ;

    private final String code;
    private final String desc;

    TrxTypeCodeEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }


    public static boolean isExitTxn(String trxType) {
        return EXIT.code.equals(trxType) || EXIT_OVERTIME.code.equals(trxType);
    }


    public static boolean isEntryTxn(String trxType) {
        return ENTRY.code.equals(trxType);
    }
}
