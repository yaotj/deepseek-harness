package com.chinasofti.huateng.facepay.api.device;

/**
 * 对外 {@code paymentResult} 取值，逐字照搬旧 {@code DevicePayCodeEnum}。
 * 设备按这些值决定出票 / 提示失败 / 继续轮询。
 *
 * <p>与内部状态的映射关系（NEVER 直接把内部状态吐给设备）：</p>
 * <ul>
 *   <li>{@code F2F_ORDER.ORDER_STATUS} 为 PAID / FULFILLED 等已收款态 → {@link #SUCCESS}</li>
 *   <li>PAY_FAILED / EXPIRED / CANCELED → {@link #FAILED}</li>
 *   <li>TVM {@code requestPayResult} 的「尚未支付」→ {@link #ORDERED}（文案「已下单」）</li>
 *   <li>扫码支付进行中、以及<b>支付中心没答上来（UNKNOWN）</b> → {@link #PROCESSING}（文案「处理中」），
 *       让设备继续轮询，NEVER 报 FAILED（钱可能已经扣了）</li>
 * </ul>
 *
 * <p><b>{@code ORDERED} 与 {@code PROCESSING} 不能互换</b>：旧实现里 TVM 的
 * {@code requestPayResult} 用 {@code ORDERED/已下单}，而 BOM 的 {@code requestGetPayResult}
 * 与两侧的 {@code requestPayment} 用 {@code PROCESSING/处理中}
 * （{@code BomOrderServiceImpl:424}、{@code TvmOrderServiceImpl:245}）。
 * 2026-09-11 新旧双打实测：同一笔未支付的 BOM 单，旧返 {@code PROCESSING}、新曾返 {@code ORDERED}
 * （{@code paymentResultDesc} 两边都是「处理中」，只有码不一样），据此拆成两个值。
 * NEVER 为了「统一枚举」再合并回去。</p>
 */
public enum PaymentResult {

    ORDERED("ORDERED", "已下单"),
    PROCESSING("PROCESSING", "处理中"),
    SUCCESS("SUCCESS", "成功"),
    FAILED("FAILED", "失败");

    private final String code;

    private final String msg;

    PaymentResult(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
