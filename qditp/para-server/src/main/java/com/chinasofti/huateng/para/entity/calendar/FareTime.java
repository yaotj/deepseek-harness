package com.chinasofti.huateng.para.entity.calendar;

import java.time.LocalDateTime;


public class FareTime {
    private Long paraVerNo;
    private Integer fareTier;
    private Integer allowedTime;
    private String lastUpdUser;
    private LocalDateTime lastUpdTms;

    public Long getParaVerNo() { return paraVerNo; }
    public void setParaVerNo(Long paraVerNo) { this.paraVerNo = paraVerNo; }
    public Integer getFareTier() { return fareTier; }
    public void setFareTier(Integer fareTier) { this.fareTier = fareTier; }
    public Integer getAllowedTime() { return allowedTime; }
    public void setAllowedTime(Integer allowedTime) { this.allowedTime = allowedTime; }
    public String getLastUpdUser() { return lastUpdUser; }
    public void setLastUpdUser(String lastUpdUser) { this.lastUpdUser = lastUpdUser; }
    public LocalDateTime getLastUpdTms() { return lastUpdTms; }
    public void setLastUpdTms(LocalDateTime lastUpdTms) { this.lastUpdTms = lastUpdTms; }

    @Override
    public String toString() {
        return "FareTime{" +
                "paraVerNo=" + paraVerNo +
                ", fareTier=" + fareTier +
                ", allowedTime=" + allowedTime +
                ", lastUpdUser='" + lastUpdUser + '\'' +
                ", lastUpdTms=" + lastUpdTms +
                '}';
    }
}
