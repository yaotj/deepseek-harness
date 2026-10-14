package com.chinasofti.huateng.recon.thirdparty;

import java.math.BigDecimal;

/**
 * 第三方/渠道侧日账单落地对象（对应 reconciliation-bill-date.csv 的 22 字段 + 入账批次）。
 *
 * <p>金额字段按渠道账单原样保留为「元」(decimal)，比对层 MUST 显式换算成「分」后再与 ITP 侧比对，
 * NEVER 在入库时隐式 ÷100 或 ×100。详见 docs/business/recon-thirdparty.md §六。</p>
 */
public class ThirdPartyBill {

    private Long id;
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBillDate() { return billDate; }
    public void setBillDate(String billDate) { this.billDate = billDate; }
    public String getPaymentChannel() { return paymentChannel; }
    public void setPaymentChannel(String paymentChannel) { this.paymentChannel = paymentChannel; }
    public String getPaymentVendor() { return paymentVendor; }
    public void setPaymentVendor(String paymentVendor) { this.paymentVendor = paymentVendor; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public String getChannelMerchantNo() { return channelMerchantNo; }
    public void setChannelMerchantNo(String channelMerchantNo) { this.channelMerchantNo = channelMerchantNo; }
    public String getTerminalNo() { return terminalNo; }
    public void setTerminalNo(String terminalNo) { this.terminalNo = terminalNo; }
    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }
    public String getTradeStatus() { return tradeStatus; }
    public void setTradeStatus(String tradeStatus) { this.tradeStatus = tradeStatus; }
    public String getTradeTime() { return tradeTime; }
    public void setTradeTime(String tradeTime) { this.tradeTime = tradeTime; }
    public String getClearingDate() { return clearingDate; }
    public void setClearingDate(String clearingDate) { this.clearingDate = clearingDate; }
    public String getMerchantOrderNo() { return merchantOrderNo; }
    public void setMerchantOrderNo(String merchantOrderNo) { this.merchantOrderNo = merchantOrderNo; }
    public String getChannelTradeNo() { return channelTradeNo; }
    public void setChannelTradeNo(String channelTradeNo) { this.channelTradeNo = channelTradeNo; }
    public String getMerchantRefundNo() { return merchantRefundNo; }
    public void setMerchantRefundNo(String merchantRefundNo) { this.merchantRefundNo = merchantRefundNo; }
    public String getChannelRefundNo() { return channelRefundNo; }
    public void setChannelRefundNo(String channelRefundNo) { this.channelRefundNo = channelRefundNo; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getOrderAmount() { return orderAmount; }
    public void setOrderAmount(BigDecimal orderAmount) { this.orderAmount = orderAmount; }
    public BigDecimal getTransactionAmount() { return transactionAmount; }
    public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) { this.feeAmount = feeAmount; }
    public BigDecimal getSettlementAmount() { return settlementAmount; }
    public void setSettlementAmount(BigDecimal settlementAmount) { this.settlementAmount = settlementAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }

    /** 关联键：(商户订单号, 渠道交易流水号)。 */
    public String joinKey() {
        return (merchantOrderNo == null ? "" : merchantOrderNo) + "\u0001" + (channelTradeNo == null ? "" : channelTradeNo);
    }
}
