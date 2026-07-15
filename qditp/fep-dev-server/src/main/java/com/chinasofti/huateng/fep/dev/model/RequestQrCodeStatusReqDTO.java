package com.chinasofti.huateng.fep.dev.model;

/**
 * IF1A-04 查询票卡状态请求业务参数。
 */
public class RequestQrCodeStatusReqDTO {

    private String itpUserId;
    private String cardId;
    private String trxType;
    private String ticketTransSeq;
    private String qrType;

    public String getItpUserId() {
        return itpUserId;
    }

    public void setItpUserId(String itpUserId) {
        this.itpUserId = itpUserId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getTrxType() {
        return trxType;
    }

    public void setTrxType(String trxType) {
        this.trxType = trxType;
    }

    public String getTicketTransSeq() {
        return ticketTransSeq;
    }

    public void setTicketTransSeq(String ticketTransSeq) {
        this.ticketTransSeq = ticketTransSeq;
    }

    public String getQrType() {
        return qrType;
    }

    public void setQrType(String qrType) {
        this.qrType = qrType;
    }
}
