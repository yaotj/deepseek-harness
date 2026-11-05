package com.chinasofti.huateng.facepay.api.device.bom;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

public class RequestQrCodeStatusReqDTO extends BaseDeviceRequest {

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

    @Override
    public String toString() {
        return "RequestQrCodeStatusReqDTO{itpUserId=" + itpUserId
                + ", cardId=" + cardId
                + ", trxType=" + trxType
                + ", ticketTransSeq=" + ticketTransSeq
                + ", qrType=" + qrType
                + ", deviceId=" + getDeviceId() + '}';
    }
}
