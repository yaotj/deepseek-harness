package com.chinasofti.huateng.paysign.support;

/** 支付渠道的**处理类别**（2026-09-16，ADR-D109）。 */
public sealed interface PaymentChannel {

    /** 钱包（{@code 0B}）：**没有支付中心代扣协议以外的签约咨询链路**，解绑由账户域。 */
    record Wallet(String code) implements PaymentChannel {
    }

    /** 走支付中心代扣签约的渠道（支付宝 / 微信 / 数币 …）。 */
    record Contracted(String code) implements PaymentChannel {
    }

    /** 归一化后的渠道编码；{@link Contracted} 上可能为 {@code null}。 */
    String code();
}
