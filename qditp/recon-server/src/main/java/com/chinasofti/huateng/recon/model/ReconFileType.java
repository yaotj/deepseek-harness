package com.chinasofti.huateng.recon.model;

public enum ReconFileType {
    EXP("ITP.EXP"),
    PAY("ITP.PAY"),
    BUS("ITP.BUS"),
    DETAIL("ITP.DETAIL");

    private final String prefix;

    ReconFileType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
