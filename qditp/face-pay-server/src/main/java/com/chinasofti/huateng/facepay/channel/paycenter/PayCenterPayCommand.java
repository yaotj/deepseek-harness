package com.chinasofti.huateng.facepay.channel.paycenter;

/**
 * 预下单入参。
 *
 * @param orderNo       ITP 订单号，同时作为支付中心的 merchantOrderNo 幂等键
 * @param scene         支付场景，决定报文形态，见 {@link PayScene}
 * @param paymentVendor 支付方式。{@link PayScene#QRCODE} 时旧实现<b>固定 {@code 0C}</b>，
 *                      调用方传 {@code null} 即用该默认值；其余场景必填
 * @param payType       {@code 0}-其他支付方式，{@code 1}-数字人民币 APP。
 *                      {@link PayScene#SCAN} 不传该字段（旧实现如此），传了也会被忽略
 * @param amount        金额，<b>单位分</b>
 * @param subject       订单标题
 * @param body          订单描述
 * @param authCode      用户付款码，仅 {@link PayScene#SCAN} 必填
 */
public record PayCenterPayCommand(
        String orderNo,
        PayScene scene,
        String paymentVendor,
        String payType,
        long amount,
        String subject,
        String body,
        String authCode) {

    /** {@link PayScene#QRCODE} 的支付方式默认值，来源旧 {@code buildTvmPayRequest} 硬编码。 */
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

    /** {@link PayScene#QRCODE} 未指定支付方式时回落到 {@code 0C}。 */
    public String effectivePaymentVendor() {
        return paymentVendor == null || paymentVendor.isBlank() ? DEFAULT_QRCODE_PAYMENT_VENDOR : paymentVendor;
    }
}
