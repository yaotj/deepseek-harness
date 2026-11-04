package com.chinasofti.huateng.recon.model.page;

import lombok.Data;

import java.math.BigDecimal;

/** 业务清分明细行（对 {@code RECON_THIRD_BILL} 按「账单日 + 支付渠道/商户」聚合的纯查询结果）。 */
@Data
public class ReconClearingRowView {
    private String billDate;
    private String paymentChannel;
    private String paymentVendor;
    private String channelName;
    private long txnCount;
    private BigDecimal orderAmount;
    private BigDecimal transactionAmount;
    private BigDecimal feeAmount;
    private BigDecimal settlementAmount;
    private BigDecimal discountAmount;
}
