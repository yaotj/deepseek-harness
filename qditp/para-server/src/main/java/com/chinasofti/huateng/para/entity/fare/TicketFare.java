package com.chinasofti.huateng.para.entity.fare;

import java.time.LocalDateTime;

public class TicketFare {
    private Long paraVerNo;
    private Integer ticketType;
    private String chipType;
    private Integer fareGroupNo;
    private String lastUpdUser;
    private LocalDateTime lastUpdTms;

    public Long getParaVerNo() { return paraVerNo; }
    public void setParaVerNo(Long paraVerNo) { this.paraVerNo = paraVerNo; }
    public Integer getTicketType() { return ticketType; }
    public void setTicketType(Integer ticketType) { this.ticketType = ticketType; }
    public String getChipType() { return chipType; }
    public void setChipType(String chipType) { this.chipType = chipType; }
    public Integer getFareGroupNo() { return fareGroupNo; }
    public void setFareGroupNo(Integer fareGroupNo) { this.fareGroupNo = fareGroupNo; }
    public String getLastUpdUser() { return lastUpdUser; }
    public void setLastUpdUser(String lastUpdUser) { this.lastUpdUser = lastUpdUser; }
    public LocalDateTime getLastUpdTms() { return lastUpdTms; }
    public void setLastUpdTms(LocalDateTime lastUpdTms) { this.lastUpdTms = lastUpdTms; }
}
