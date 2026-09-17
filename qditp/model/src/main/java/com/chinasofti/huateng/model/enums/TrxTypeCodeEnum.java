package com.chinasofti.huateng.model.enums;

/**
 * 交易类型编码枚举。
 */
public enum TrxTypeCodeEnum {

    /** 01 - 进站。 */
    ENTRY("01", "进站"),

    /** 02 - 出站（正常） */
    EXIT("02", "出站"),

    /** 03 - 超时出站。 */
    EXIT_OVERTIME("03", "超时出站"),

    /** 04 - 进站失败。 */
    ENTRY_FAIL("04", "进站失败"),

    /** 99 - 异常。 */
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

    /**
     * 判断是否为出站交易（正常出站或超时出站）。
     */
    public static boolean isExitTxn(String trxType) {
        return EXIT.code.equals(trxType) || EXIT_OVERTIME.code.equals(trxType);
    }

    /**
     * 判断是否为进站交易。
     */
    public static boolean isEntryTxn(String trxType) {
        return ENTRY.code.equals(trxType);
    }
}
