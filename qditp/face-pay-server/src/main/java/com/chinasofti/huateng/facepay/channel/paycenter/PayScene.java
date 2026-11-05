package com.chinasofti.huateng.facepay.channel.paycenter;

public enum PayScene {

    QRCODE("qrcode"),
    SCAN("scan"),
    APP("app");

    private final String code;

    PayScene(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
