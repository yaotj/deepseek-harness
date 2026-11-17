package com.chinasofti.huateng.recon.model.page;

import lombok.Data;

import java.math.BigDecimal;


@Data
public class ReconDailyStatView {
    private String groupKey;
    private String paymentVendor;
    private long txnCount;
    private BigDecimal amountSum;
    private BigDecimal cashAmountSum;
    private BigDecimal couponAmountSum;
    private BigDecimal refundAmountSum;
}
