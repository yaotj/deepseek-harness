package com.chinasofti.huateng.acc.es.server.enumns;

public enum TaskExecuteStat {

    UN_EXECUTED("0","未执行"),EXECUTED("1","执行中"),FAILURE("2","失败"),
    COMPLETED("3","完成"),CANCELED("9","取消");

    private String code;

    private String label;

    private TaskExecuteStat(String code,String label) {
        this.code = code;
        this.label = label;
    }
    public String code() {
        return this.code;
    }

    public String label() {
        return this.label;
    }
}
