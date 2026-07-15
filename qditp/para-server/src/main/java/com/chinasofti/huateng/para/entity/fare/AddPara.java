package com.chinasofti.huateng.para.entity.fare;

import java.time.LocalDateTime;

public class AddPara {
    private Long paraVerNo;
    private Integer bankMinAmt;
    private Integer bankMaxAmt;
    private String lastUpdUser;
    private LocalDateTime lastUpdTms;

    public Long getParaVerNo() { return paraVerNo; }
    public void setParaVerNo(Long paraVerNo) { this.paraVerNo = paraVerNo; }
    public Integer getBankMinAmt() { return bankMinAmt; }
    public void setBankMinAmt(Integer bankMinAmt) { this.bankMinAmt = bankMinAmt; }
    public Integer getBankMaxAmt() { return bankMaxAmt; }
    public void setBankMaxAmt(Integer bankMaxAmt) { this.bankMaxAmt = bankMaxAmt; }
    public String getLastUpdUser() { return lastUpdUser; }
    public void setLastUpdUser(String lastUpdUser) { this.lastUpdUser = lastUpdUser; }
    public LocalDateTime getLastUpdTms() { return lastUpdTms; }
    public void setLastUpdTms(LocalDateTime lastUpdTms) { this.lastUpdTms = lastUpdTms; }
}
