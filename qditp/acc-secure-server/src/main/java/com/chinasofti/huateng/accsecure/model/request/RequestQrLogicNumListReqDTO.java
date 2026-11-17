package com.chinasofti.huateng.accsecure.model.request;


public class RequestQrLogicNumListReqDTO {

    private String requestNum;

    private String requestSeq;

    private String ticketType;

    public String getRequestNum() {
        return requestNum;
    }

    public void setRequestNum(String requestNum) {
        this.requestNum = requestNum;
    }

    public String getRequestSeq() { return requestSeq; }
    public void setRequestSeq(String requestSeq) { this.requestSeq = requestSeq; }
    public String getTicketType() { return ticketType; }
    public void setTicketType(String ticketType) { this.ticketType = ticketType; }
}
