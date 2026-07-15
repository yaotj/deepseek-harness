package com.chinasofti.huateng.para.entity.calendar;

import java.time.LocalDateTime;

/**
 * 特殊日期参数表 TBL_SPECIAL_DATE。
 */
public class SpecialDate {
    private Long paraVerNo;
    private Integer seqNo;
    private String dateType;
    private String specialDate;
    private String lastUpdUser;
    private LocalDateTime lastUpdTms;

    public Long getParaVerNo() { return paraVerNo; }
    public void setParaVerNo(Long paraVerNo) { this.paraVerNo = paraVerNo; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public String getDateType() { return dateType; }
    public void setDateType(String dateType) { this.dateType = dateType; }
    public String getSpecialDate() { return specialDate; }
    public void setSpecialDate(String specialDate) { this.specialDate = specialDate; }
    public String getLastUpdUser() { return lastUpdUser; }
    public void setLastUpdUser(String lastUpdUser) { this.lastUpdUser = lastUpdUser; }
    public LocalDateTime getLastUpdTms() { return lastUpdTms; }
    public void setLastUpdTms(LocalDateTime lastUpdTms) { this.lastUpdTms = lastUpdTms; }

    @Override
    public String toString() {
        return "SpecialDate{" +
                "paraVerNo=" + paraVerNo +
                ", seqNo=" + seqNo +
                ", dateType='" + dateType + '\'' +
                ", specialDate='" + specialDate + '\'' +
                ", lastUpdUser='" + lastUpdUser + '\'' +
                ", lastUpdTms=" + lastUpdTms +
                '}';
    }
}
