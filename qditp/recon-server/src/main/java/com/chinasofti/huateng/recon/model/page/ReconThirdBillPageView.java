package com.chinasofti.huateng.recon.model.page;

import lombok.Data;

import java.math.BigDecimal;

/** 渠道账单行（{@code RECON_THIRD_BILL}）运营查询视图。 */
@Data
public class ReconThirdBillPageView {
    private String billDate;
    private String paymentChannel;
    private String paymentVendor;
    private String channelName;
    private String channelMerchantNo;
    private String terminalNo;
    private String businessType;
    private String tradeStatus;
    private String tradeTime;
    private String clearingDate;
    private String merchantOrderNo;
    private String channelTradeNo;
    private String merchantRefundNo;
    private String channelRefundNo;
    private String currency;
    private BigDecimal orderAmount;
    private BigDecimal transactionAmount;
    private BigDecimal feeAmount;
    private BigDecimal settlementAmount;
    private BigDecimal discountAmount;
    private String productName;
    private String remark;
    private String batchNo;
}
