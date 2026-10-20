package com.chinasofti.huateng.facepay.api.page;

/**
 * 运营端设备心跳状态分页查询视图对象（表 {@code F2F_DEVICE_STATUS}）。
 *
 * <p>一行对应一台设备的最新心跳，表本身只存最新状态不存历史，故没有「某个时刻的状态」这种查询。
 */
public class F2fDeviceStatusPageVO {

    /** 受理渠道：01-APP，02-TVM，03-BOM。 */
    private String channel;

    /** 设备号，复合主键之一；前 4 位内嵌车站码。 */
    private String deviceId;

    /** 车站编码，由心跳接口按设备号前 4 位补算后落库；历史行可能仍为空。 */
    private String stationCode;

    /** 车站中文名，按 {@code STATION_CODE} 关联 {@code STATION_INFO}；该表未覆盖的站返回 null。 */
    private String stationName;

    /** 在线标志：1-在线，0-离线，由扫表按心跳超时判定。 */
    private String onlineFlag;

    /** 最近一次心跳时间，格式 {@code YYYY-MM-DD HH24:MI:SS}。 */
    private String lastHeartbeatTms;

    /** 距最近一次心跳的秒数，由 DB 时钟现算，避免前端时区差异。 */
    private Long idleSeconds;

    /** 累计心跳次数，每次心跳自增 1。 */
    private Long heartbeatCount;

    /** 本行最后一次更新时间，格式 {@code YYYY-MM-DD HH24:MI:SS}。 */
    private String updateTms;

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }

    public String getStationName() { return stationName; }
    public void setStationName(String stationName) { this.stationName = stationName; }

    public String getOnlineFlag() { return onlineFlag; }
    public void setOnlineFlag(String onlineFlag) { this.onlineFlag = onlineFlag; }

    public String getLastHeartbeatTms() { return lastHeartbeatTms; }
    public void setLastHeartbeatTms(String lastHeartbeatTms) { this.lastHeartbeatTms = lastHeartbeatTms; }

    public Long getIdleSeconds() { return idleSeconds; }
    public void setIdleSeconds(Long idleSeconds) { this.idleSeconds = idleSeconds; }

    public Long getHeartbeatCount() { return heartbeatCount; }
    public void setHeartbeatCount(Long heartbeatCount) { this.heartbeatCount = heartbeatCount; }

    public String getUpdateTms() { return updateTms; }
    public void setUpdateTms(String updateTms) { this.updateTms = updateTms; }
}
