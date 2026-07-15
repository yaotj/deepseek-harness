package com.chinasofti.huateng.para.entity.calendar;

import java.time.LocalDateTime;

/**
 * 时间段参数表 TBL_TIME_INTERVAL。
 */
public class TimeInterval {
    private Long paraVerNo;
    private Integer intervalNo;
    private String beginTime;
    private String endTime;
    private String lastUpdUser;
    private LocalDateTime lastUpdTms;

    public Long getParaVerNo() { return paraVerNo; }
    public void setParaVerNo(Long paraVerNo) { this.paraVerNo = paraVerNo; }
    public Integer getIntervalNo() { return intervalNo; }
    public void setIntervalNo(Integer intervalNo) { this.intervalNo = intervalNo; }
    public String getBeginTime() { return beginTime; }
    public void setBeginTime(String beginTime) { this.beginTime = beginTime; }
    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }
    public String getLastUpdUser() { return lastUpdUser; }
    public void setLastUpdUser(String lastUpdUser) { this.lastUpdUser = lastUpdUser; }
    public LocalDateTime getLastUpdTms() { return lastUpdTms; }
    public void setLastUpdTms(LocalDateTime lastUpdTms) { this.lastUpdTms = lastUpdTms; }

    @Override
    public String toString() {
        return "TimeInterval{" +
                "paraVerNo=" + paraVerNo +
                ", intervalNo=" + intervalNo +
                ", beginTime='" + beginTime + '\'' +
                ", endTime='" + endTime + '\'' +
                ", lastUpdUser='" + lastUpdUser + '\'' +
                ", lastUpdTms=" + lastUpdTms +
                '}';
    }
}
