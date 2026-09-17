package com.chinasofti.huateng.gatetxnpay.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** GATE_TXN_PAY 过闸扣费业务订单实体。 */
public class GateTxnPay {
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
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

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
    private String payChannelCode;
    private String paymentVendor;
    private String channelType;
    private String payUserId;
    private String transferFlag;
    private String cumulativeType;
    private Integer originalFare;
    private Integer walletTotalAmt;
    private Integer discountLevelAmt;
    private BigDecimal discountRate;
    private Integer expectedGateAmount;
    private String discountCalcStatus;
    private String discountCalcMsg;
    private String industryDetail;

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
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

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
    public String getPayChannelCode() { return payChannelCode; }
    public void setPayChannelCode(String payChannelCode) { this.payChannelCode = payChannelCode; }
    public String getPaymentVendor() { return paymentVendor; }
    public void setPaymentVendor(String paymentVendor) { this.paymentVendor = paymentVendor; }
    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }
    public String getPayUserId() { return payUserId; }
    public void setPayUserId(String payUserId) { this.payUserId = payUserId; }
    public String getTransferFlag() { return transferFlag; }
    public void setTransferFlag(String transferFlag) { this.transferFlag = transferFlag; }
    public String getCumulativeType() { return cumulativeType; }
    public void setCumulativeType(String cumulativeType) { this.cumulativeType = cumulativeType; }
    public Integer getOriginalFare() { return originalFare; }
    public void setOriginalFare(Integer originalFare) { this.originalFare = originalFare; }
    public Integer getWalletTotalAmt() { return walletTotalAmt; }
    public void setWalletTotalAmt(Integer walletTotalAmt) { this.walletTotalAmt = walletTotalAmt; }
    public Integer getDiscountLevelAmt() { return discountLevelAmt; }
    public void setDiscountLevelAmt(Integer discountLevelAmt) { this.discountLevelAmt = discountLevelAmt; }
    public BigDecimal getDiscountRate() { return discountRate; }
    public void setDiscountRate(BigDecimal discountRate) { this.discountRate = discountRate; }
    public Integer getExpectedGateAmount() { return expectedGateAmount; }
    public void setExpectedGateAmount(Integer expectedGateAmount) { this.expectedGateAmount = expectedGateAmount; }
    public String getDiscountCalcStatus() { return discountCalcStatus; }
    public void setDiscountCalcStatus(String discountCalcStatus) { this.discountCalcStatus = discountCalcStatus; }
    public String getDiscountCalcMsg() { return discountCalcMsg; }
    public void setDiscountCalcMsg(String discountCalcMsg) { this.discountCalcMsg = discountCalcMsg; }
    public String getIndustryDetail() { return industryDetail; }
    public void setIndustryDetail(String industryDetail) { this.industryDetail = industryDetail; }
}
