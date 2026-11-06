package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/**
 * 「按原扣费订单号反查补款单」的联表投影（{@code SUPPLEMENT_ORDER_ITEM} join {@code SUPPLEMENT_ORDER}）。
 *
 * <p>一行 = 一条补款明细与其所属补款单的合并视图。综管台扣费明细页据 {@link #origOrderNo}
 * 回填「补款金额 / 补款支付渠道 / 补款订单号」三列，所以这里只带展示需要的字段，
 * NEVER 把补款单整行搬过来（{@code SUPPLEMENT_ORDER} 有 20+ 列，多数与运营无关）。
 *
 * <p>一个原扣费订单可能对应多条补款明细（{@code UK(ORDER_NO, ORIG_ORDER_NO)} 只保证同一补款单内不重复），
 * 调用方 MUST 按 {@link #origOrderNo} 分组后再决定展示口径，NEVER 假定一一对应。
 */
public class SupplementOrigRef {

    /** 原扣费订单号，等于 {@code GATE_TXN_PAY.ORDER_NO}。 */
    private String origOrderNo;

    /** 补款单号（SP 前缀）。 */
    private String supplementOrderNo;

    /** 补款金额，单位分。 */
    private Long totalAmount;

    /** 补款支付状态：INIT / PROCESSING / SUCCESS / FAIL / CLOSED。 */
    private String payStatus;

    /**
     * 补款支付渠道，取 {@code SUPPLEMENT_ORDER.SIGN_CHANNEL_CODE}。
     *
     * <p>业主裁决（2026-11-06）：三列并存的渠道字段里以本列为准。
     * 同表另有 {@code PAYMENT_VENDOR}（支付商户码值空间）与 {@code PAY_CHANNEL_CODE}
     * （实体有字段、测试库无该列且历史数据取不到值），**NEVER 混用** —— 三者码值空间不同。
     */
    private String signChannelCode;

    /** 补款明细收口状态：PENDING / SETTLED / FAILED。 */
    private String settleStatus;

    /** 补款交易日期 yyyyMMdd。 */
    private String txnDate;

    private LocalDateTime payTime;

    public String getOrigOrderNo() { return origOrderNo; }
    public void setOrigOrderNo(String origOrderNo) { this.origOrderNo = origOrderNo; }

    public String getSupplementOrderNo() { return supplementOrderNo; }
    public void setSupplementOrderNo(String supplementOrderNo) { this.supplementOrderNo = supplementOrderNo; }

    public Long getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Long totalAmount) { this.totalAmount = totalAmount; }

    public String getPayStatus() { return payStatus; }
    public void setPayStatus(String payStatus) { this.payStatus = payStatus; }

    public String getSignChannelCode() { return signChannelCode; }
    public void setSignChannelCode(String signChannelCode) { this.signChannelCode = signChannelCode; }

    public String getSettleStatus() { return settleStatus; }
    public void setSettleStatus(String settleStatus) { this.settleStatus = settleStatus; }

    public String getTxnDate() { return txnDate; }
    public void setTxnDate(String txnDate) { this.txnDate = txnDate; }

    public LocalDateTime getPayTime() { return payTime; }
    public void setPayTime(LocalDateTime payTime) { this.payTime = payTime; }
}
