package com.chinasofti.huateng.para.model;

import java.time.LocalDateTime;

/** 当前生效参数的版本信息，一条记录对应一种参数类型（如路网拓扑、费率）。 */
public class LineStationVersion {
    /** 参数类型名称，如“路网拓扑”“费率”。 */
    private String paraTypeName;
    /** 该参数类型的当前版本号。 */
    private Long versionNo;
    /** 当前生效的参数文件名。 */
    private String fileName;
    /** 该参数记录的最后更新时间。 */
    private LocalDateTime updateTime;
    /** 该参数记录的生效时间（14 位 yyyyMMddHHmmss）。 */
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
