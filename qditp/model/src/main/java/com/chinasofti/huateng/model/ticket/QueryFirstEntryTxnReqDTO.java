package com.chinasofti.huateng.model.ticket;

/** 查询同二维码交易序列号对应的首笔进站交易。 */
public class QueryFirstEntryTxnReqDTO {
    private String cardId;
    private String ticketTransSeq;

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getTicketTransSeq() { return ticketTransSeq; }
    public void setTicketTransSeq(String ticketTransSeq) { this.ticketTransSeq = ticketTransSeq; }
}
