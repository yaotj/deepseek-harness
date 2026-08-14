package com.chinasofti.huateng.model.enums;

/**
 * 设备类型定义枚举。
 * <p>对应表2　设备类型定义表</p>
 */
public enum DeviceTypeEnum {

    /** 01 - ACC服务器 (0x01) */
    ACC_SERVER("01", "ACC服务器"),

    /** 02 - LCC服务器 (0x02) */
    LCC_SERVER("02", "LCC服务器"),

    /** 03 - SC服务器 (0x03) */
    SC_SERVER("03", "SC服务器"),

    /** 04 - 进站AGM (0x04) */
    AGM_IN("04", "进站AGM"),

    /** 05 - 出站AGM (0x05) */
    AGM_OUT("05", "出站AGM"),

    /** 06 - 双向AGM (0x06) */
    AGM_BIDIRECTIONAL("06", "双向AGM"),

    /** 07 - TVMⅠ (0x07) */
    TVM_1("07", "TVMⅠ"),

    /** 08 - BOM (0x08) */
    BOM("08", "BOM"),

    /** 09 - PCA (0x09) */
    PCA("09", "PCA"),

    /** 10 - TCM (0x0A) */
    TCM("10", "TCM"),

    /** 11 - ES (0x0B) */
    ES("11", "ES"),

    /** 12 - ITP服务器 (0x0C) */
    ITP_SERVER("12", "ITP服务器"),

    /** 13 - TVMⅡ (0x0D) */
    TVM_2("13", "TVMⅡ"),

    /** 14 - STT (0x0E) */
    STT("14", "STT"),

    // 15~31 (0x0F~0x1F) 保留

    /** 32 - ACC工作站 (0x20) */
    ACC_WORKSTATION("32", "ACC工作站"),

    /** 33 - LCC工作站 (0x21) */
    LCC_WORKSTATION("33", "LCC工作站"),

    /** 34 - SC工作站 (0x22) */
    SC_WORKSTATION("34", "SC工作站"),

    /** 35 - ITP工作站 (0x23) */
    ITP_WORKSTATION("35", "ITP工作站"),

    /** 36 - 自助补站手机 (0x24) */
    SELF_SERVICE_GATE("36", "自助补站手机");

    private final String code;
    private final String desc;

    DeviceTypeEnum(String code, String desc) {
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
     * 根据编码获取设备类型枚举。
     */
    public static DeviceTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (DeviceTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断是否为AGM设备。
     */
    public boolean isAgm() {
        return this == AGM_IN || this == AGM_OUT || this == AGM_BIDIRECTIONAL;
    }

    /**
     * 判断是否为TVM设备。
     */
    public boolean isTvm() {
        return this == TVM_1 || this == TVM_2;
    }

    /**
     * 判断是否为BOM设备。
     */
    public boolean isBom() {
        return this == BOM;
    }

    /**
     * 判断是否为服务器或工作站。
     */
    public boolean isServerOrWorkstation() {
        return this == ACC_SERVER || this == LCC_SERVER || this == SC_SERVER
            || this == ITP_SERVER || this == ACC_WORKSTATION
            || this == LCC_WORKSTATION || this == SC_WORKSTATION
            || this == ITP_WORKSTATION;
    }
}
