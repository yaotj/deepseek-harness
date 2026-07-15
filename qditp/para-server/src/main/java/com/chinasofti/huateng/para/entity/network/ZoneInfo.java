package com.chinasofti.huateng.para.entity.network;

/**
 * 区域信息参数表 TBL_ZONE_INFO。
 */
public class ZoneInfo {
    private Long paraVerNo;
    private Integer zoneNo;
    private String zoneName;
    private String zoneEName;

    public Long getParaVerNo() {
        return paraVerNo;
    }

    public void setParaVerNo(Long paraVerNo) {
        this.paraVerNo = paraVerNo;
    }

    public Integer getZoneNo() {
        return zoneNo;
    }

    public void setZoneNo(Integer zoneNo) {
        this.zoneNo = zoneNo;
    }

    public String getZoneName() {
        return zoneName;
    }

    public void setZoneName(String zoneName) {
        this.zoneName = zoneName;
    }

    public String getZoneEName() {
        return zoneEName;
    }

    public void setZoneEName(String zoneEName) {
        this.zoneEName = zoneEName;
    }

    @Override
    public String toString() {
        return "ZoneInfo{" +
                "paraVerNo=" + paraVerNo +
                ", zoneNo=" + zoneNo +
                ", zoneName='" + zoneName + '\'' +
                ", zoneEName='" + zoneEName + '\'' +
                '}';
    }
}
