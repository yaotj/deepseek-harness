package com.chinasofti.huateng.recon.model.page;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 日统计行（对 {@code PAY_TXN_DETAIL} 纯查询聚合，不建聚合表）。
 *
 * <p>groupBy=VENDOR：按「交易日期 + 支付账户（PAYMENT_VENDOR）」聚合；
 * groupBy=HOUR：按「当天小时」聚合（统计日必须单日，hourBucket 为 00~23，未知为 '--'）。
 */
@Data
public class ReconDailyStatView {
    /** groupBy=VENDOR 时为交易日期（YYYYMMDD），groupBy=HOUR 时为小时桶。 */
    private String groupKey;
    /** 支付账户 / 支付商户（PAYMENT_VENDOR），HOUR 口径下为当天全部账户合计。 */
    private String paymentVendor;
    private long txnCount;
    private BigDecimal amountSum;
    private BigDecimal cashAmountSum;
    private BigDecimal couponAmountSum;
    private BigDecimal refundAmountSum;
}
