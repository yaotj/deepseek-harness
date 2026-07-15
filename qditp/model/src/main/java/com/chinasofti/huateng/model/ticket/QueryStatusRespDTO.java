package com.chinasofti.huateng.model.ticket;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * @author zzm
 * @date 2026/5/25 14:23
 */
public class QueryStatusRespDTO extends CommonResult {

    /**
     * 第三方用户ID
     */
    private String thirdUserId;

    /**
     * ITP用户ID
     */
    private String cardId;

    /**
     * 乘车状态
     */
    private String status;

    /**
     * 进站车站
     */
    private String gateInStation;

    /**
     * 进站时间
     */
    private String gateInTime;

    /**
     * 末次交易车站
     */
    private String lastTxnStation;

    /**
     * 末次交易时间
     */
    private String lastTxnTime;

    /**
     * 交易流水号
     */
    private String txnSeq;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getGateInStation() {
        return gateInStation;
    }

    public void setGateInStation(String gateInStation) {
        this.gateInStation = gateInStation;
    }

    public String getGateInTime() {
        return gateInTime;
    }

    public void setGateInTime(String gateInTime) {
        this.gateInTime = gateInTime;
    }

    public String getLastTxnStation() {
        return lastTxnStation;
    }

    public void setLastTxnStation(String lastTxnStation) {
        this.lastTxnStation = lastTxnStation;
    }

    public String getLastTxnTime() {
        return lastTxnTime;
    }

    public void setLastTxnTime(String lastTxnTime) {
        this.lastTxnTime = lastTxnTime;
    }

    public String getTxnSeq() {
        return txnSeq;
    }

    public void setTxnSeq(String txnSeq) {
        this.txnSeq = txnSeq;
    }
}
