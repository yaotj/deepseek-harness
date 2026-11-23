package com.chinasofti.huateng.heartbeat.model;

import java.time.LocalDateTime;

/**
 * 心跳历史日志实体（DEVICE_HEARTBEAT_LOG）
 */
public class DeviceHeartbeatLog {
    private Long id;
    private String channel;
    private String deviceId;
    private String stationCode;
    private LocalDateTime heartbeatTms;
    private String reqBizData;
    private LocalDateTime createTms;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public LocalDateTime getHeartbeatTms() {
        return heartbeatTms;
    }

    public void setHeartbeatTms(LocalDateTime heartbeatTms) {
        this.heartbeatTms = heartbeatTms;
    }

    public String getReqBizData() {
        return reqBizData;
    }

    public void setReqBizData(String reqBizData) {
        this.reqBizData = reqBizData;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }
}
