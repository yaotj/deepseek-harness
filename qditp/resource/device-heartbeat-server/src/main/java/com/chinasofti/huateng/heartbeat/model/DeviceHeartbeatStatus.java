package com.chinasofti.huateng.heartbeat.model;

import java.time.LocalDateTime;

/**
 * 设备最新心跳状态实体（DEVICE_HEARTBEAT_STATUS）
 */
public class DeviceHeartbeatStatus {
    private String channel;
    private String deviceId;
    private String stationCode;
    private LocalDateTime lastHeartbeatTms;
    private Long heartbeatCount;
    private String onlineFlag;
    private LocalDateTime createTms;
    private LocalDateTime updateTms;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public LocalDateTime getLastHeartbeatTms() {
        return lastHeartbeatTms;
    }

    public void setLastHeartbeatTms(LocalDateTime lastHeartbeatTms) {
        this.lastHeartbeatTms = lastHeartbeatTms;
    }

    public Long getHeartbeatCount() {
        return heartbeatCount;
    }

    public void setHeartbeatCount(Long heartbeatCount) {
        this.heartbeatCount = heartbeatCount;
    }

    public String getOnlineFlag() {
        return onlineFlag;
    }

    public void setOnlineFlag(String onlineFlag) {
        this.onlineFlag = onlineFlag;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public LocalDateTime getUpdateTms() {
        return updateTms;
    }

    public void setUpdateTms(LocalDateTime updateTms) {
        this.updateTms = updateTms;
    }
}
