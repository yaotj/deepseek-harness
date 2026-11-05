package com.chinasofti.huateng.facepay.channel.paycenter;

public record PayCenterPayCommand(
        String orderNo,
        PayScene scene,
        String paymentVendor,
        String payType,
        long amount,
        String subject,
        String body,
        String authCode) {

    public static final String DEFAULT_QRCODE_PAYMENT_VENDOR = "0C";

    public PayCenterPayCommand {
        if (orderNo == null || orderNo.isBlank()) {
            throw new IllegalArgumentException("orderNo 必填");
        }
        if (scene == null) {
            throw new IllegalArgumentException("scene 必填");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("amount 必须为正数，单位分, amount=" + amount);
        }
        if (scene == PayScene.SCAN && (authCode == null || authCode.isBlank())) {
            throw new IllegalArgumentException("scene=scan 必须带 authCode, orderNo=" + orderNo);
        }
        if (scene != PayScene.QRCODE && (paymentVendor == null || paymentVendor.isBlank())) {
            throw new IllegalArgumentException("scene=" + scene.getCode() + " 必须带 paymentVendor, orderNo=" + orderNo);
        }
    }

    public String effectivePaymentVendor() {
        return paymentVendor == null || paymentVendor.isBlank() ? DEFAULT_QRCODE_PAYMENT_VENDOR : paymentVendor;
    }
}
