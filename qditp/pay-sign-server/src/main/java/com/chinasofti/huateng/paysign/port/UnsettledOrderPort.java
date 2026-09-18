package com.chinasofti.huateng.paysign.port;

import java.time.LocalDateTime;

/**
 * 支付域看**闸机域「有没有未结清扣费订单」方向**的窄接口（防腐层，2026-09-17，ADR-D119）。
 *
 * <p>解约链路的前置闸门：有欠费就不许解约。与 {@link DebitSyncPort} 分成两个端口的理由见那边的注释。
 */
public interface UnsettledOrderPort {

    /**
     * 查该用户该支付渠道下是否还有未结清扣费订单。
     *
     * @param requestTime 解约申请时间，允许 {@code null}（扫表补偿链路不带该字段）
     * @return 三态答复；<b>NEVER 把「问不出来」折叠成 {@code false}</b>，理由见 {@link UnsettledOrderAnswer}
     */
    UnsettledOrderAnswer hasUnsettledOrder(String thirdUserId, String paymentVendor, LocalDateTime requestTime);
}
