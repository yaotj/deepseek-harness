package com.chinasofti.huateng.para.entity.device;

import java.time.LocalDateTime;


public class DeviceInfo {
    private String deviceId;

    private String lineId;

    private String lineName;

    private String stationId;

    private String stationName;

    private String deviceType;

    private String deviceTypeName;

    private String deviceName;

    private String ipAddr;

    private Integer port;

    private String isVirtual;

    private String accIsDeleted;

    private LocalDateTime accUpdateTime;

    private String accManageLevel;

    private String manageMode;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime syncTime;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getLineId() { return lineId; }
    public void setLineId(String lineId) { this.lineId = lineId; }
    public String getLineName() { return lineName; }
    public void setLineName(String lineName) { this.lineName = lineName; }
    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getDeviceTypeName() { return deviceTypeName; }
    public void setDeviceTypeName(String deviceTypeName) { this.deviceTypeName = deviceTypeName; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getIpAddr() { return ipAddr; }
    public void setIpAddr(String ipAddr) { this.ipAddr = ipAddr; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public String getIsVirtual() { return isVirtual; }
    public void setIsVirtual(String isVirtual) { this.isVirtual = isVirtual; }
    public String getAccIsDeleted() { return accIsDeleted; }
    public void setAccIsDeleted(String accIsDeleted) { this.accIsDeleted = accIsDeleted; }
    public LocalDateTime getAccUpdateTime() { return accUpdateTime; }
    public void setAccUpdateTime(LocalDateTime accUpdateTime) { this.accUpdateTime = accUpdateTime; }
    public String getAccManageLevel() { return accManageLevel; }
    public void setAccManageLevel(String accManageLevel) { this.accManageLevel = accManageLevel; }
    public String getManageMode() { return manageMode; }
    public void setManageMode(String manageMode) { this.manageMode = manageMode; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public LocalDateTime getSyncTime() { return syncTime; }
    public void setSyncTime(LocalDateTime syncTime) { this.syncTime = syncTime; }
}
