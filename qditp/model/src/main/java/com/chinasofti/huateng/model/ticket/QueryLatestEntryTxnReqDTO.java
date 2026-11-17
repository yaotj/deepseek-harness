package com.chinasofti.huateng.model.ticket;


public class QueryLatestEntryTxnReqDTO {
    private String cardId;

    private String exitHandleDateTime;

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getExitHandleDateTime() { return exitHandleDateTime; }
    public void setExitHandleDateTime(String exitHandleDateTime) { this.exitHandleDateTime = exitHandleDateTime; }
}
