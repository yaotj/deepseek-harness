package com.chinasofti.huateng.paysign.entity;

import java.time.LocalDateTime;

/**
 * PAY_REFUND_DETAIL 退款明细实体。
 */
public class PayRefundDetail {
    private Long id;
    private String refundOrderNo;
    private String orderNo;
    private String refundStatus;
    private Integer refundAmount;
    private String refundReason;
    private String merchantRefundNo;
    private String refundNo;
    private String channelRefundNo;
    private Integer requestCount;
    private LocalDateTime nextRequestTime;
    private LocalDateTime lastRequestTime;
    private String refundTime;
    private String txnDate;
    private String retCode;
    private String retMsg;
    private String payCenterCode;
    private String payCenterMsg;
    private String requestBody;
    private String responseBody;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRefundOrderNo() { return refundOrderNo; }
    public void setRefundOrderNo(String refundOrderNo) { this.refundOrderNo = refundOrderNo; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }
    public Integer getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Integer refundAmount) { this.refundAmount = refundAmount; }
    public String getRefundReason() { return refundReason; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
    public String getMerchantRefundNo() { return merchantRefundNo; }
    public void setMerchantRefundNo(String merchantRefundNo) { this.merchantRefundNo = merchantRefundNo; }
    public String getRefundNo() { return refundNo; }
    public void setRefundNo(String refundNo) { this.refundNo = refundNo; }
    public String getChannelRefundNo() { return channelRefundNo; }
    public void setChannelRefundNo(String channelRefundNo) { this.channelRefundNo = channelRefundNo; }
    public Integer getRequestCount() { return requestCount; }
    public void setRequestCount(Integer requestCount) { this.requestCount = requestCount; }
    public LocalDateTime getNextRequestTime() { return nextRequestTime; }
    public void setNextRequestTime(LocalDateTime nextRequestTime) { this.nextRequestTime = nextRequestTime; }
    public LocalDateTime getLastRequestTime() { return lastRequestTime; }
    public void setLastRequestTime(LocalDateTime lastRequestTime) { this.lastRequestTime = lastRequestTime; }
    public String getRefundTime() { return refundTime; }
    public void setRefundTime(String refundTime) { this.refundTime = refundTime; }
    public String getTxnDate() { return txnDate; }
    public void setTxnDate(String txnDate) { this.txnDate = txnDate; }
    public String getRetCode() { return retCode; }
    public void setRetCode(String retCode) { this.retCode = retCode; }
    public String getRetMsg() { return retMsg; }
    public void setRetMsg(String retMsg) { this.retMsg = retMsg; }
    public String getPayCenterCode() { return payCenterCode; }
    public void setPayCenterCode(String payCenterCode) { this.payCenterCode = payCenterCode; }
    public String getPayCenterMsg() { return payCenterMsg; }
    public void setPayCenterMsg(String payCenterMsg) { this.payCenterMsg = payCenterMsg; }
    public String getRequestBody() { return requestBody; }
    public void setRequestBody(String requestBody) { this.requestBody = requestBody; }
    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
