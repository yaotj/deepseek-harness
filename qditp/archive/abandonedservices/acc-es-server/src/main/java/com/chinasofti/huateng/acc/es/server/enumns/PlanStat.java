package com.chinasofti.huateng.acc.es.server.enumns;

public enum PlanStat {

    APPLYING("01","申请中"),
    AGREE("02","审批通过"),
    REFUSE("03","审批未通过"),
    SPLITING("04","拆分中"),
    SPLITED("05","拆分完成"),
    UNEXECUTED("5","未执行"),
    EXECUTED("6","已执行");

    private String code;

    private String label;

    private PlanStat(String code,String label) {
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
