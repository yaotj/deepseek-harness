package com.chinasofti.huateng.para.entity.fare;

public class BaseFare {
    private Long paraVerNo;
    private Integer fareType;
    private Integer fareTier;
    private Integer ticketPrice;

    public Long getParaVerNo() { return paraVerNo; }
    public void setParaVerNo(Long paraVerNo) { this.paraVerNo = paraVerNo; }
    public Integer getFareType() { return fareType; }
    public void setFareType(Integer fareType) { this.fareType = fareType; }
    public Integer getFareTier() { return fareTier; }
    public void setFareTier(Integer fareTier) { this.fareTier = fareTier; }
    public Integer getTicketPrice() { return ticketPrice; }
    public void setTicketPrice(Integer ticketPrice) { this.ticketPrice = ticketPrice; }
}
