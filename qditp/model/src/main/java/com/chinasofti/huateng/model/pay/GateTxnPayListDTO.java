package com.chinasofti.huateng.model.pay;

import java.time.LocalDateTime;

/**
 * IF8A-05 交易记录列表 RPC 传输对象。
 *
 * <p>由 gate-txn-pay-server 序列化后供 ticket-server 等跨模块查询，避免直接依赖领域实体。</p>
 */
public class GateTxnPayListDTO {
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
    private String payChannelCode;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

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
    public String getPayChannelCode() { return payChannelCode; }
    public void setPayChannelCode(String payChannelCode) { this.payChannelCode = payChannelCode; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
