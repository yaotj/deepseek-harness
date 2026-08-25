package com.chinasofti.huateng.ticket.entity;

import java.time.LocalDateTime;

/**
 * 对应 QRCODE_TXN_DETAIL 表。
 */
public class QRCodeTxnDetail {
    private Long id;
    private String deviceId;
    private String itpUserId;
    private String trxType;
    private String issueChannelCode;
    private String signChannelCode;
    private String cardId;
    private String cardType;
    private String handleDateTime;
    private String txnDate;
    private String handleStationCode;
    private String handleStationName;
    private Long trxAmount;
    private Long overtimeAmount;
    private String lastTicketStatus;
    private String handleResultCode;
    private String lastHandleStationCode;
    private String lastHandleStationName;
    private String lastHandleDateTime;
    private String ticketTransSeq;
    private String reserve1;
    private String reserve2;
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getItpUserId() {
        return itpUserId;
    }

    public void setItpUserId(String itpUserId) {
        this.itpUserId = itpUserId;
    }

    public String getTrxType() {
        return trxType;
    }

    public void setTrxType(String trxType) {
        this.trxType = trxType;
    }

    public String getIssueChannelCode() {
        return issueChannelCode;
    }

    public void setIssueChannelCode(String issueChannelCode) {
        this.issueChannelCode = issueChannelCode;
    }

    public String getSignChannelCode() {
        return signChannelCode;
    }

    public void setSignChannelCode(String signChannelCode) {
        this.signChannelCode = signChannelCode;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public String getHandleDateTime() {
        return handleDateTime;
    }

    public void setHandleDateTime(String handleDateTime) {
        this.handleDateTime = handleDateTime;
    }

    public String getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(String txnDate) {
        this.txnDate = txnDate;
    }

    public String getHandleStationCode() {
        return handleStationCode;
    }

    public void setHandleStationCode(String handleStationCode) {
        this.handleStationCode = handleStationCode;
    }

    public String getHandleStationName() {
        return handleStationName;
    }

    public void setHandleStationName(String handleStationName) {
        this.handleStationName = handleStationName;
    }

    public Long getTrxAmount() {
        return trxAmount;
    }

    public void setTrxAmount(Long trxAmount) {
        this.trxAmount = trxAmount;
    }

    public Long getOvertimeAmount() {
        return overtimeAmount;
    }

    public void setOvertimeAmount(Long overtimeAmount) {
        this.overtimeAmount = overtimeAmount;
    }

    public String getLastTicketStatus() {
        return lastTicketStatus;
    }

    public void setLastTicketStatus(String lastTicketStatus) {
        this.lastTicketStatus = lastTicketStatus;
    }

    public String getHandleResultCode() {
        return handleResultCode;
    }

    public void setHandleResultCode(String handleResultCode) {
        this.handleResultCode = handleResultCode;
    }

    public String getLastHandleStationCode() {
        return lastHandleStationCode;
    }

    public void setLastHandleStationCode(String lastHandleStationCode) {
        this.lastHandleStationCode = lastHandleStationCode;
    }

    public String getLastHandleStationName() {
        return lastHandleStationName;
    }

    public void setLastHandleStationName(String lastHandleStationName) {
        this.lastHandleStationName = lastHandleStationName;
    }

    public String getLastHandleDateTime() {
        return lastHandleDateTime;
    }

    public void setLastHandleDateTime(String lastHandleDateTime) {
        this.lastHandleDateTime = lastHandleDateTime;
    }

    public String getTicketTransSeq() {
        return ticketTransSeq;
    }

    public void setTicketTransSeq(String ticketTransSeq) {
        this.ticketTransSeq = ticketTransSeq;
    }

    public String getReserve1() {
        return reserve1;
    }

    public void setReserve1(String reserve1) {
        this.reserve1 = reserve1;
    }

    public String getReserve2() {
        return reserve2;
    }

    public void setReserve2(String reserve2) {
        this.reserve2 = reserve2;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "QRCodeTxnDetail{" +
                "id=" + id +
                ", deviceId='" + deviceId + '\'' +
                ", cardId='" + cardId + '\'' +
                ", cardType='" + cardType + '\'' +
                ", trxType='" + trxType + '\'' +
                ", issueChannelCode='" + issueChannelCode + '\'' +
                ", signChannelCode='" + signChannelCode + '\'' +
                ", handleDateTime='" + handleDateTime + '\'' +
                ", txnDate='" + txnDate + '\'' +
                ", handleStationCode='" + handleStationCode + '\'' +
                ", handleStationName='" + handleStationName + '\'' +
                ", trxAmount=" + trxAmount +
                ", overtimeAmount=" + overtimeAmount +
                ", lastTicketStatus='" + lastTicketStatus + '\'' +
                ", handleResultCode='" + handleResultCode + '\'' +
                ", lastHandleStationCode='" + lastHandleStationCode + '\'' +
                ", lastHandleStationName='" + lastHandleStationName + '\'' +
                ", lastHandleDateTime='" + lastHandleDateTime + '\'' +
                ", ticketTransSeq='" + ticketTransSeq + '\'' +
                ", reserve1='" + (reserve1 != null && reserve1.length() > 8 ? reserve1.substring(0, 8) + "..." : reserve1) + '\'' +
                ", reserve2='" + reserve2 + '\'' +
                ", createTime=" + createTime +
                '}';
    }
}
