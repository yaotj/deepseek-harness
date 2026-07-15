package com.chinasofti.huateng.acc.es.server.enumns;

/**
 * Description:设备状态
 *
 * @author
 * @date 2020/7/17 16:02
 */
public enum EsStat {

    NORMAL("0","工作"),
    PAUSE("1","暂停"),
    MALFUNCTION("3","故障");


    private String key;
    private String value;

    private EsStat(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
