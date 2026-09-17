package com.chinasofti.huateng.ticket.entity;

import java.time.LocalDateTime;

/** IF5A-03 补站请求台账（{@code QRCODE_SUPPLEMENT_REQUEST}）。 */
public class SupplementRequest {

    /** 处理中，已声明但闸机结果尚未回来。 */
    public static final String STATUS_PENDING = "PENDING";

    /** 闸机返回 0000。 */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /** 闸机明确拒绝（非 0000），结果确定，可由 BOM 重新发起。 */
    public static final String STATUS_REJECTED = "REJECTED";

    /** 结果未知（超时 / 连不上 / 无响应体）， */
    public static final String STATUS_UNKNOWN = "UNKNOWN";

    private String cardId;
    private String txnSeq;
    private String adviceOpt;
    private String codeStatusSnapshot;
    private String handleStationCode;
    private String handleDateTime;
    private String trxAmount;
    private String handleStatus;
    private String failReason;
    private Integer retryCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getTxnSeq() {
        return txnSeq;
    }

    public void setTxnSeq(String txnSeq) {
        this.txnSeq = txnSeq;
    }

    public String getAdviceOpt() {
        return adviceOpt;
    }

    public void setAdviceOpt(String adviceOpt) {
        this.adviceOpt = adviceOpt;
    }

    public String getCodeStatusSnapshot() {
        return codeStatusSnapshot;
    }

    public void setCodeStatusSnapshot(String codeStatusSnapshot) {
        this.codeStatusSnapshot = codeStatusSnapshot;
    }

    public String getHandleStationCode() {
        return handleStationCode;
    }

    public void setHandleStationCode(String handleStationCode) {
        this.handleStationCode = handleStationCode;
    }

    public String getHandleDateTime() {
        return handleDateTime;
    }

    public void setHandleDateTime(String handleDateTime) {
        this.handleDateTime = handleDateTime;
    }

    public String getTrxAmount() {
        return trxAmount;
    }

    public void setTrxAmount(String trxAmount) {
        this.trxAmount = trxAmount;
    }

    public String getHandleStatus() {
        return handleStatus;
    }

    public void setHandleStatus(String handleStatus) {
        this.handleStatus = handleStatus;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
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
}
