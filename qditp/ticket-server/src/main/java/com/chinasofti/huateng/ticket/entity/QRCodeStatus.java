package com.chinasofti.huateng.ticket.entity;

import java.time.LocalDateTime;

import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;

/**
 * 对应 QRCODE_STATUS 表。
 */
public class QRCodeStatus {
    private String cardId;
    private Integer useCount;
    private String channel;
    private String codeStatus;
    private String gateInTime;
    private String gateInStation;
    private String lastTxnTime;
    private String lastTxnStation;
    private String txnSeq;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String gateStatus;
    private Long trxAmount;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public Integer getUseCount() {
        return useCount;
    }

    public void setUseCount(Integer useCount) {
        this.useCount = useCount;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getCodeStatus() {
        return codeStatus;
    }

    public void setCodeStatus(String codeStatus) {
        this.codeStatus = codeStatus;
    }

    public QRCodeStatusEnum getCodeStatusEnum() {
        return QRCodeStatusEnum.fromCode(codeStatus);
    }

    public String getGateInTime() {
        return gateInTime;
    }

    public void setGateInTime(String gateInTime) {
        this.gateInTime = gateInTime;
    }

    public String getGateInStation() {
        return gateInStation;
    }

    public void setGateInStation(String gateInStation) {
        this.gateInStation = gateInStation;
    }

    public String getLastTxnTime() {
        return lastTxnTime;
    }

    public void setLastTxnTime(String lastTxnTime) {
        this.lastTxnTime = lastTxnTime;
    }

    public String getLastTxnStation() {
        return lastTxnStation;
    }

    public void setLastTxnStation(String lastTxnStation) {
        this.lastTxnStation = lastTxnStation;
    }

    public String getTxnSeq() {
        return txnSeq;
    }

    public void setTxnSeq(String txnSeq) {
        this.txnSeq = txnSeq;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getGateStatus() {
        return gateStatus;
    }

    public void setGateStatus(String gateStatus) {
        this.gateStatus = gateStatus;
    }

    public Long getTrxAmount() {
        return trxAmount;
    }

    public void setTrxAmount(Long trxAmount) {
        this.trxAmount = trxAmount;
    }

    @Override
    public String toString() {
        return "QRCodeStatus{" +
                "cardId='" + cardId + '\'' +
                ", useCount=" + useCount +
                ", channel='" + channel + '\'' +
                ", codeStatus='" + codeStatus + '\'' +
                ", gateInTime='" + gateInTime + '\'' +
                ", gateInStation='" + gateInStation + '\'' +
                ", lastTxnTime='" + lastTxnTime + '\'' +
                ", lastTxnStation='" + lastTxnStation + '\'' +
                ", txnSeq='" + txnSeq + '\'' +
                ", createTime=" + createTime +
                ", updateTime=" + updateTime +
                ", gateStatus='" + gateStatus + '\'' +
                ", trxAmount=" + trxAmount +
                '}';
    }
}
