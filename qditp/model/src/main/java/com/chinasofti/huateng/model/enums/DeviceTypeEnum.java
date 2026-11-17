package com.chinasofti.huateng.model.enums;


public enum DeviceTypeEnum {

    ACC_SERVER("01", "ACC服务器"),

    LCC_SERVER("02", "LCC服务器"),

    SC_SERVER("03", "SC服务器"),

    AGM_IN("04", "进站AGM"),

    AGM_OUT("05", "出站AGM"),

    AGM_BIDIRECTIONAL("06", "双向AGM"),

    TVM_1("07", "TVMⅠ"),

    BOM("08", "BOM"),

    PCA("09", "PCA"),

    TCM("10", "TCM"),

    ES("11", "ES"),

    ITP_SERVER("12", "ITP服务器"),

    TVM_2("13", "TVMⅡ"),

    STT("14", "STT"),


    ACC_WORKSTATION("32", "ACC工作站"),

    LCC_WORKSTATION("33", "LCC工作站"),

    SC_WORKSTATION("34", "SC工作站"),

    ITP_WORKSTATION("35", "ITP工作站"),

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


    public boolean isAgm() {
        return this == AGM_IN || this == AGM_OUT || this == AGM_BIDIRECTIONAL;
    }


    public boolean isTvm() {
        return this == TVM_1 || this == TVM_2;
    }


    public boolean isBom() {
        return this == BOM;
    }


    public boolean isServerOrWorkstation() {
        return this == ACC_SERVER || this == LCC_SERVER || this == SC_SERVER
            || this == ITP_SERVER || this == ACC_WORKSTATION
            || this == LCC_WORKSTATION || this == SC_WORKSTATION
            || this == ITP_WORKSTATION;
    }
}
