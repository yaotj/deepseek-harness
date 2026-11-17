package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;


public class SupplementOrigRef {

    private String origOrderNo;

    private String supplementOrderNo;

    private Long totalAmount;

    private String payStatus;


    private String signChannelCode;

    private String settleStatus;

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
