package com.chinasofti.huateng.facepay.api.page;

public class F2fDeviceStatusPageVO {

    private String channel;

    private String deviceId;

    private String stationCode;

    private String stationName;

    private String onlineFlag;

    private String lastHeartbeatTms;

    private Long idleSeconds;

    private Long heartbeatCount;

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
