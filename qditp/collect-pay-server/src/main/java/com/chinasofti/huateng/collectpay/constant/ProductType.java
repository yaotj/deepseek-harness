package com.chinasofti.huateng.collectpay.constant;

import java.util.HashMap;
import java.util.Map;

/** 产品类型枚举，用于订单号前缀。 */
public enum ProductType {
    ordinaryTicket("00", "一票通_单程票"),
    sjtDistanceTicket("01", "一票通_计程票"),
    sjtTimesTicket("02", "一票通_计次票"),
    sjtDateTicket("03", "一票通_计期票"),
    sjtEmployeeTicket("07", "一票通_员工票"),
    tvmTopup("08", "一票通_琴岛通卡"),
    qingdaoBankTicket("09", "一票通_青岛银行金融IC卡"),
    communication("0A", "一票通_交通部互联互通卡"),
    SUPPLEMENT("0B", "补充票款"),
    CmbSUPPLEMENT("0C", "招行一网通_垫资补缴"),
    EcnyActPay("0D", "招行一网通_垫资补缴"),
    countingTicket("0E", "虚拟电子票");

    private String code;
    private String val;
    private static final Map<String, ProductType> map;

    private ProductType(String code, String val) {
        this.code = code;
        this.val = val;
    }

    static {
        map = new HashMap<>();
        for (ProductType code : values()) {
            map.put(code.getCode(), code);
        }
    }

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getType() {
        return this.val;
    }

    public void setType(String type) {
        this.val = type;
    }

    public static ProductType getCode(String code) {
        return (ProductType) map.get(code);
    }
}
