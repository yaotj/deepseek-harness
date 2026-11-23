package com.chinasofti.huateng.para.entity.risk;

/**
 * 同一自然日出站事件达阈值的聚合行（000002 出站规则派生源）。
 */
public class RepeatExitDay {
    private String cardId;
    private String txnDate;
    private int exitCount;
    private String lastExitTime;
    private String lastExitSeq;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(String txnDate) {
        this.txnDate = txnDate;
    }

    public int getExitCount() {
        return exitCount;
    }

    public void setExitCount(int exitCount) {
        this.exitCount = exitCount;
    }

    public String getLastExitTime() {
        return lastExitTime;
    }

    public void setLastExitTime(String lastExitTime) {
        this.lastExitTime = lastExitTime;
    }

    public String getLastExitSeq() {
        return lastExitSeq;
    }

    public void setLastExitSeq(String lastExitSeq) {
        this.lastExitSeq = lastExitSeq;
    }
}
