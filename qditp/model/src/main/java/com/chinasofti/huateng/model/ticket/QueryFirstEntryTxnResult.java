package com.chinasofti.huateng.model.ticket;

import com.chinasofti.huateng.common.response.CommonResult;

/** 同序列号首笔进站交易的计费所需字段。 */
public class QueryFirstEntryTxnResult extends CommonResult {
    private String cardId;
    private String ticketTransSeq;
    private String handleDateTime;
    private String handleStationCode;

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getTicketTransSeq() { return ticketTransSeq; }
    public void setTicketTransSeq(String ticketTransSeq) { this.ticketTransSeq = ticketTransSeq; }
    public String getHandleDateTime() { return handleDateTime; }
    public void setHandleDateTime(String handleDateTime) { this.handleDateTime = handleDateTime; }
    public String getHandleStationCode() { return handleStationCode; }
    public void setHandleStationCode(String handleStationCode) { this.handleStationCode = handleStationCode; }
}
