package com.chinasofti.huateng.para.model.device;

/**
 * ACC 设备配置接口的单条设备记录（{@code GET /api/v1/Config/device} 返回的 data 元素）。
 * <p>字段名与 ACC 返回 JSON 一致，用 Fastjson2 反序列化；时间字符串由同步服务自行解析。</p>
 */
public class AccDeviceDTO {
    private String lineId;
    private String lineName;
    private String stationId;
    private String stationName;
    private String deviceTypeCode;
    private String deviceTypeName;
    private String deviceId;
    private String deviceName;
    private String ip;
    private Integer port;
    private Boolean isDeleted;
    /** ACC 侧更新时间，格式 yyyy-MM-dd HH:mm:ss，保留原文由服务层解析。 */
    private String updateTime;
    /** 坐标与角度，当前仅透传不落库。 */
    private Integer coorX;
    private Integer coorY;
    private Integer angle;
    private Boolean isVirture;
    private Boolean isLocal;

    public String getLineId() { return lineId; }
    public void setLineId(String lineId) { this.lineId = lineId; }
    public String getLineName() { return lineName; }
    public void setLineName(String lineName) { this.lineName = lineName; }
    public String getStationId() { return stationId; }
    public void setStationId(String stationId) { this.stationId = stationId; }
    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }
    public String getDeviceTypeCode() { return deviceTypeCode; }
    public void setDeviceTypeCode(String deviceTypeCode) { this.deviceTypeCode = deviceTypeCode; }
    public String getDeviceTypeName() { return deviceTypeName; }
    public void setDeviceTypeName(String deviceTypeName) { this.deviceTypeName = deviceTypeName; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getIp() { return ip; }
    public void setIp(String ip) { this.ip = ip; }
    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }
    public Boolean getDeleted() { return isDeleted; }
    public void setDeleted(Boolean deleted) { isDeleted = deleted; }
    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
    public Integer getCoorX() { return coorX; }
    public void setCoorX(Integer coorX) { this.coorX = coorX; }
    public Integer getCoorY() { return coorY; }
    public void setCoorY(Integer coorY) { this.coorY = coorY; }
    public Integer getAngle() { return angle; }
    public void setAngle(Integer angle) { this.angle = angle; }
    public Boolean getVirture() { return isVirture; }
    public void setVirture(Boolean virture) { isVirture = virture; }
    public Boolean getLocal() { return isLocal; }
    public void setLocal(Boolean local) { isLocal = local; }
}
