package com.chinasofti.huateng.paysign.page;


public class PayRefundOrderView {

    private String orderNo;

    private String refundOrderNo;


    private String refundStatus;

    private Long refundAmount;

    private String refundTime;

    private String txnDate;

    private Integer requestCount;

    private String payCenterCode;

    private String payCenterMsg;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getRefundOrderNo() { return refundOrderNo; }
    public void setRefundOrderNo(String refundOrderNo) { this.refundOrderNo = refundOrderNo; }

    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }

    public Long getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Long refundAmount) { this.refundAmount = refundAmount; }

    public String getRefundTime() { return refundTime; }
    public void setRefundTime(String refundTime) { this.refundTime = refundTime; }

    public String getTxnDate() { return txnDate; }
    public void setTxnDate(String txnDate) { this.txnDate = txnDate; }

    public Integer getRequestCount() { return requestCount; }
    public void setRequestCount(Integer requestCount) { this.requestCount = requestCount; }

    public String getPayCenterCode() { return payCenterCode; }
    public void setPayCenterCode(String payCenterCode) { this.payCenterCode = payCenterCode; }

    public String getPayCenterMsg() { return payCenterMsg; }
    public void setPayCenterMsg(String payCenterMsg) { this.payCenterMsg = payCenterMsg; }
}
