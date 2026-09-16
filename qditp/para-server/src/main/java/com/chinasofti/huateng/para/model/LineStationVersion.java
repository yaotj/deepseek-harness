package com.chinasofti.huateng.para.model;

import java.time.LocalDateTime;

/** 当前路网参数对应的线路、车站代码版本信息。 */
public class LineStationVersion {
    private Long lineCodeVersion;
    private Long stationCodeVersion;
    private String networkFileName;
    private String rateFileName;
    private LocalDateTime updateTime;
    private String effectiveTime;

    public Long getLineCodeVersion() { return lineCodeVersion; }
    public void setLineCodeVersion(Long lineCodeVersion) { this.lineCodeVersion = lineCodeVersion; }
    public Long getStationCodeVersion() { return stationCodeVersion; }
    public void setStationCodeVersion(Long stationCodeVersion) { this.stationCodeVersion = stationCodeVersion; }
    public String getNetworkFileName() { return networkFileName; }
    public void setNetworkFileName(String networkFileName) { this.networkFileName = networkFileName; }
    public String getRateFileName() { return rateFileName; }
    public void setRateFileName(String rateFileName) { this.rateFileName = rateFileName; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getEffectiveTime() { return effectiveTime; }
    public void setEffectiveTime(String effectiveTime) { this.effectiveTime = effectiveTime; }
}
