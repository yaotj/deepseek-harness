package com.chinasofti.huateng.para.model;

import java.time.LocalDateTime;

public class LineStationVersion {
    private String paraTypeName;
    private Long versionNo;
    private String fileName;
    private LocalDateTime updateTime;
    private String effectiveTime;

    public String getParaTypeName() { return paraTypeName; }
    public void setParaTypeName(String paraTypeName) { this.paraTypeName = paraTypeName; }
    public Long getVersionNo() { return versionNo; }
    public void setVersionNo(Long versionNo) { this.versionNo = versionNo; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public String getEffectiveTime() { return effectiveTime; }
    public void setEffectiveTime(String effectiveTime) { this.effectiveTime = effectiveTime; }
}
