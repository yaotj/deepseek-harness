package com.chinasofti.huateng.facepay.channel.paycenter;

/** 支付中心 {@code scene} 取值。 */
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
