package com.chinasofti.huateng.heartbeat.model;

import java.time.LocalDateTime;

/**
 * 设备心跳请求DTO
 */
public class HeartbeatRequest {
    /** 设备ID，格式：站码前4位 + 站内序号 */
    private String deviceId;
    /** 业务数据（可选，原样存储快照） */
    private String bizData;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getBizData() {
        return bizData;
    }

    public void setBizData(String bizData) {
        this.bizData = bizData;
    }
}
