package com.chinasofti.huateng.para.entity.fare;

public class FareMatrix {
    private Long paraVerNo;
    private String beginStatCode;
    private String endStatCode;
    private Integer fareTier;

    public Long getParaVerNo() { return paraVerNo; }
    public void setParaVerNo(Long paraVerNo) { this.paraVerNo = paraVerNo; }
    public String getBeginStatCode() { return beginStatCode; }
    public void setBeginStatCode(String beginStatCode) { this.beginStatCode = beginStatCode; }
    public String getEndStatCode() { return endStatCode; }
    public void setEndStatCode(String endStatCode) { this.endStatCode = endStatCode; }
    public Integer getFareTier() { return fareTier; }
    public void setFareTier(Integer fareTier) { this.fareTier = fareTier; }
}
