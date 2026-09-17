package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/** 设备最新心跳状态（表 F2F_DEVICE_STATUS）。 */
public class F2fDeviceStatus {

    /** 受理渠道，与 F2F_ORDER.CHANNEL 同口径：01-APP，02-TVM，03-BOM。 */
    private String channel;

    /** 设备号。 */
    private String deviceId;

    /** 设备所在车站编码。 */
    private String stationCode;

    /** 最近一次心跳时间。 */
    private LocalDateTime lastHeartbeatTms;

    /** 累计心跳次数，每次 MERGE 命中已有行时自增 1。 */
    private Long heartbeatCount;

    /** 1在线，0离线；由扫表按LAST_HEARTBEAT_TMS超时判定。 */
    private String onlineFlag;

    /** 该设备首次上报心跳（行首次写入）的时间。 */
    private LocalDateTime createTms;

    /** 本行最后一次更新时间。 */
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
