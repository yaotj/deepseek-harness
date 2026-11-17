package com.chinasofti.huateng.model.ticket;

import com.chinasofti.huateng.common.response.CommonResult;


public class QueryStatusRespDTO extends CommonResult {


    private String thirdUserId;


    private String cardId;


    private String status;


    private String gateInStation;


    private String gateInTime;


    private String lastTxnStation;


    private String lastTxnTime;


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
