package com.chinasofti.huateng.ticket.service.impl;

import com.chinasofti.huateng.model.app.TransRecordDTO;

import java.math.BigDecimal;

/**
 * GATE_TXN_PAY + PAY_TXN_DETAIL 双源交易记录中间模型。
 *
 * <p>Mapper 查询返回该类型，由 {@link TransRecordAssembler} 转换为 APP 应答 DTO。</p>
 */
public class TransListEntry {
    // ========== 来自 GATE_TXN_PAY ==========
    private Long id;
    private String orderNo;
    private String debitStatus;
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String deviceId;
    private String trxType;
    private String ticketTransSeq;
    private String inStation;
    private String inTime;
    private String outStation;
    private String outTime;
    private String txnDate;
    private Integer trxAmount;
    private Integer overtimeAmount;
    private Integer totalAmount;
    private String issueChannelCode;
    private String signChannelCode;
    private String ticketStatus;
    private String entryStationName;
    private String exitStationName;
    private String orderExpType;
    private String companionFlag;
    private String offlineFlag;
    private String ticketCode;
    private Integer countingTimes;
    private String countingFlag;
    private String attributableParty;
    private String receivingParty;
    private String remark;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;

    // ========== 来自 PAY_TXN_DETAIL ==========
    private String payType;
    private String payStatus;
    private String paymentVendor;
    private String requestSignSeq;
    private Integer amount;
    private Integer cashAmount;
    private Integer couponAmount;
    private String refundStatus;
    private Integer refundAmount;
    private java.time.LocalDateTime lastRefundTime;
    private String merchantOrderNo;
    private String channelOrderNo;
    private String payUserId;
    private Integer requestCount;
    private java.time.LocalDateTime nextRequestTime;
    private java.time.LocalDateTime lastRequestTime;
    private java.time.LocalDateTime firstRequestTime;
    private java.time.LocalDateTime responseTime;
    private String payTime;
    private String payTxnDate;
    private java.time.LocalDateTime payCreateTime;
    private java.time.LocalDateTime payUpdateTime;
    private String payDebitRequestResult;
    private String payDiscountInfo;
    private Integer payDiscountFee;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getDebitStatus() { return debitStatus; }
    public void setDebitStatus(String debitStatus) { this.debitStatus = debitStatus; }
    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getTrxType() { return trxType; }
    public void setTrxType(String trxType) { this.trxType = trxType; }
    public String getTicketTransSeq() { return ticketTransSeq; }
    public void setTicketTransSeq(String ticketTransSeq) { this.ticketTransSeq = ticketTransSeq; }
    public String getInStation() { return inStation; }
    public void setInStation(String inStation) { this.inStation = inStation; }
    public String getInTime() { return inTime; }
    public void setInTime(String inTime) { this.inTime = inTime; }
    public String getOutStation() { return outStation; }
    public void setOutStation(String outStation) { this.outStation = outStation; }
    public String getOutTime() { return outTime; }
    public void setOutTime(String outTime) { this.outTime = outTime; }
    public String getTxnDate() { return txnDate; }
    public void setTxnDate(String txnDate) { this.txnDate = txnDate; }
    public Integer getTrxAmount() { return trxAmount; }
    public void setTrxAmount(Integer trxAmount) { this.trxAmount = trxAmount; }
    public Integer getOvertimeAmount() { return overtimeAmount; }
    public void setOvertimeAmount(Integer overtimeAmount) { this.overtimeAmount = overtimeAmount; }
    public Integer getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Integer totalAmount) { this.totalAmount = totalAmount; }
    public String getIssueChannelCode() { return issueChannelCode; }
    public void setIssueChannelCode(String issueChannelCode) { this.issueChannelCode = issueChannelCode; }
    public String getSignChannelCode() { return signChannelCode; }
    public void setSignChannelCode(String signChannelCode) { this.signChannelCode = signChannelCode; }
    public String getTicketStatus() { return ticketStatus; }
    public void setTicketStatus(String ticketStatus) { this.ticketStatus = ticketStatus; }
    public String getEntryStationName() { return entryStationName; }
    public void setEntryStationName(String entryStationName) { this.entryStationName = entryStationName; }
    public String getExitStationName() { return exitStationName; }
    public void setExitStationName(String exitStationName) { this.exitStationName = exitStationName; }
    public String getOrderExpType() { return orderExpType; }
    public void setOrderExpType(String orderExpType) { this.orderExpType = orderExpType; }
    public String getCompanionFlag() { return companionFlag; }
    public void setCompanionFlag(String companionFlag) { this.companionFlag = companionFlag; }
    public String getOfflineFlag() { return offlineFlag; }
    public void setOfflineFlag(String offlineFlag) { this.offlineFlag = offlineFlag; }
    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }
    public Integer getCountingTimes() { return countingTimes; }
    public void setCountingTimes(Integer countingTimes) { this.countingTimes = countingTimes; }
    public String getCountingFlag() { return countingFlag; }
    public void setCountingFlag(String countingFlag) { this.countingFlag = countingFlag; }
    public String getAttributableParty() { return attributableParty; }
    public void setAttributableParty(String attributableParty) { this.attributableParty = attributableParty; }
    public String getReceivingParty() { return receivingParty; }
    public void setReceivingParty(String receivingParty) { this.receivingParty = receivingParty; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public java.time.LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(java.time.LocalDateTime createTime) { this.createTime = createTime; }
    public java.time.LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(java.time.LocalDateTime updateTime) { this.updateTime = updateTime; }

    public String getPayType() { return payType; }
    public void setPayType(String payType) { this.payType = payType; }
    public String getPayStatus() { return payStatus; }
    public void setPayStatus(String payStatus) { this.payStatus = payStatus; }
    public String getPaymentVendor() { return paymentVendor; }
    public void setPaymentVendor(String paymentVendor) { this.paymentVendor = paymentVendor; }
    public String getRequestSignSeq() { return requestSignSeq; }
    public void setRequestSignSeq(String requestSignSeq) { this.requestSignSeq = requestSignSeq; }
    public Integer getAmount() { return amount; }
    public void setAmount(Integer amount) { this.amount = amount; }
    public Integer getCashAmount() { return cashAmount; }
    public void setCashAmount(Integer cashAmount) { this.cashAmount = cashAmount; }
    public Integer getCouponAmount() { return couponAmount; }
    public void setCouponAmount(Integer couponAmount) { this.couponAmount = couponAmount; }
    public String getRefundStatus() { return refundStatus; }
    public void setRefundStatus(String refundStatus) { this.refundStatus = refundStatus; }
    public Integer getRefundAmount() { return refundAmount; }
    public void setRefundAmount(Integer refundAmount) { this.refundAmount = refundAmount; }
    public java.time.LocalDateTime getLastRefundTime() { return lastRefundTime; }
    public void setLastRefundTime(java.time.LocalDateTime lastRefundTime) { this.lastRefundTime = lastRefundTime; }
    public String getMerchantOrderNo() { return merchantOrderNo; }
    public void setMerchantOrderNo(String merchantOrderNo) { this.merchantOrderNo = merchantOrderNo; }
    public String getChannelOrderNo() { return channelOrderNo; }
    public void setChannelOrderNo(String channelOrderNo) { this.channelOrderNo = channelOrderNo; }
    public String getPayUserId() { return payUserId; }
    public void setPayUserId(String payUserId) { this.payUserId = payUserId; }
    public Integer getRequestCount() { return requestCount; }
    public void setRequestCount(Integer requestCount) { this.requestCount = requestCount; }
    public java.time.LocalDateTime getNextRequestTime() { return nextRequestTime; }
    public void setNextRequestTime(java.time.LocalDateTime nextRequestTime) { this.nextRequestTime = nextRequestTime; }
    public java.time.LocalDateTime getLastRequestTime() { return lastRequestTime; }
    public void setLastRequestTime(java.time.LocalDateTime lastRequestTime) { this.lastRequestTime = lastRequestTime; }
    public java.time.LocalDateTime getFirstRequestTime() { return firstRequestTime; }
    public void setFirstRequestTime(java.time.LocalDateTime firstRequestTime) { this.firstRequestTime = firstRequestTime; }
    public java.time.LocalDateTime getResponseTime() { return responseTime; }
    public void setResponseTime(java.time.LocalDateTime responseTime) { this.responseTime = responseTime; }
    public String getPayTime() { return payTime; }
    public void setPayTime(String payTime) { this.payTime = payTime; }
    public String getPayTxnDate() { return payTxnDate; }
    public void setPayTxnDate(String payTxnDate) { this.payTxnDate = payTxnDate; }
    public java.time.LocalDateTime getPayCreateTime() { return payCreateTime; }
    public void setPayCreateTime(java.time.LocalDateTime payCreateTime) { this.payCreateTime = payCreateTime; }
    public java.time.LocalDateTime getPayUpdateTime() { return payUpdateTime; }
    public void setPayUpdateTime(java.time.LocalDateTime payUpdateTime) { this.payUpdateTime = payUpdateTime; }
    public String getPayDebitRequestResult() { return payDebitRequestResult; }
    public void setPayDebitRequestResult(String payDebitRequestResult) { this.payDebitRequestResult = payDebitRequestResult; }
    public String getPayDiscountInfo() { return payDiscountInfo; }
    public void setPayDiscountInfo(String payDiscountInfo) { this.payDiscountInfo = payDiscountInfo; }
    public Integer getPayDiscountFee() { return payDiscountFee; }
    public void setPayDiscountFee(Integer payDiscountFee) { this.payDiscountFee = payDiscountFee; }
}
