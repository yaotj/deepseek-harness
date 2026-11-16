package com.chinasofti.huateng.acc.es.server.enumns;

/**
 * 签到
 */
public enum LoginStat {

    SIGN_IN("1","已签到"),
    SIGN_NO("0","未签到");



    private String key;
    private String value;

    private LoginStat(String key, String value) {
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
