package com.chinasofti.huateng.facepay.channel.paycenter;

/**
 * 支付中心 {@code scene} 取值。三态来自旧实现三个报文组装方法，不是我方设计：
 *
 * <ul>
 *   <li>{@link #QRCODE} —— 设备拉码、用户扫设备（TVM 拉码下单，旧 {@code buildTvmPayRequest}）；
 *       该分支 {@code paymentVendor} 旧实现固定写 {@code 0C}，不由入参决定。</li>
 *   <li>{@link #SCAN} —— 设备扫用户付款码（BOM 扫码支付、TVM 扫码支付都走这条，旧
 *       {@code buildBomPayRequest}）；该分支 <b>必须</b> 带 {@code authCode}，且旧实现
 *       <b>不传</b> {@code payType}。</li>
 *   <li>{@link #APP} —— APP 内支付（旧 {@code buildAppPayRequest}）。</li>
 * </ul>
 *
 * <p>STT 未接入，落地时属于哪一种取决于「谁扫谁」，需求未定，因此本枚举不预留 STT 值。</p>
 */
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
