package com.chinasofti.huateng.acc.security.feign.domain.param;


public class RequestHceCardDataParam {


    private String ticketCard;


    private String iptUserId;

    public String getTicketCard() {
        return ticketCard;
    }

    public void setTicketCard(String ticketCard) {
        this.ticketCard = ticketCard;
    }

    public String getIptUserId() {
        return iptUserId;
    }

    public void setIptUserId(String iptUserId) {
        this.iptUserId = iptUserId;
    }
}
