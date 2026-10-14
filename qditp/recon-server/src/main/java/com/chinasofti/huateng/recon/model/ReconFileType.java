package com.chinasofti.huateng.recon.model;

public enum ReconFileType {
    EXP("ITP.EXP"),
    PAY("ITP.PAY"),
    BUS("ITP.BUS"),
    DETAIL("ITP.DETAIL"),
    F2F_TXN("ITP.F2F_TXN");

    private final String prefix;

    ReconFileType(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }
}
