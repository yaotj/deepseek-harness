package com.chinasofti.huateng.para.entity.device;

import java.time.LocalDateTime;

/**
 * 设备信息参数表 TBL_DEVICE_INFO 映射。
 * <p>ACC 设备台账本地镜像 + 业主自定义管理模式；
 * ACC 来源列由同步维护，MANAGE_MODE / REMARK 仅页面维护、同步 NEVER 覆盖。</p>
 */
public class DeviceInfo {
    /** 设备编号，ACC deviceId，当前 8 位：线路2+车站2+类型2+序号2。 */
    private String deviceId;

    /** 线路编码。 */
    private String lineId;

    /** 线路名称，ACC 同步。 */
    private String lineName;

    /** 车站编码。 */
    private String stationId;

    /** 车站名称，ACC 同步。 */
    private String stationName;

    /** 设备类型编码（04 进站 AGM 等）。 */
    private String deviceType;

    /** 设备类型名称，ACC 同步。 */
    private String deviceTypeName;

    /** 设备名称，如 AGM13。 */
    private String deviceName;

    /** 设备 IP 地址。 */
    private String ipAddr;

    /** 设备端口。 */
    private Integer port;

    /** 是否虚拟设备：0 否 1 是（ACC isVirture 同步）。 */
    private String isVirtual;

    /** ACC 侧删除标记：0 正常 1 已删除（ACC isDeleted 同步，不物理删除）。 */
    private String accIsDeleted;

    /** ACC 侧最后更新时间。 */
    private LocalDateTime accUpdateTime;

    /** ACC 侧设备管理级别（码值含义待 ACC 提供）。 */
    private String accManageLevel;

    /** 业主自定义管理模式：01 正常使用 02 节能关机；同步 NEVER 覆盖本字段。 */
    private String manageMode;

    /** 备注；同步 NEVER 覆盖本字段。 */
    private String remark;

    /** 创建时间。 */
    private LocalDateTime createTime;

    /** 修改时间。 */
    private LocalDateTime updateTime;

    /** 最近一次 ACC 同步时间。 */
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
