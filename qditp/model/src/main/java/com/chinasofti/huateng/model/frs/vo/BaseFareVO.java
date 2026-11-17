package com.chinasofti.huateng.model.frs.vo;

public class BaseFareVO {
    private Integer ticketPrice;
    private String fareType;
    private String fareTier;

    public Integer getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(Integer ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getFareType() {
        return fareType;
    }

    public void setFareType(String fareType) {
        this.fareType = fareType;
    }

    public String getFareTier() {
        return fareTier;
    }

    public void setFareTier(String fareTier) {
        this.fareTier = fareTier;
    }
}
