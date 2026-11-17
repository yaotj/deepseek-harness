package com.chinasofti.huateng.recon.model.page;

import lombok.Data;

import java.math.BigDecimal;

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
