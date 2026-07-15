package com.chinasofti.huateng.online.entity;

import java.time.LocalDateTime;

public class OnlineDeviceHeartbeat {
    /**
     * 设备心跳实体。
     * 用于统一保存 BOM、AGM、TVM 等互联网终端设备的最后心跳时间与在线状态。
     */
    private String providerId;
    /** 设备所属提供方编码，如 02 TVM、03 BOM、04 AGM。 */
    private String deviceId;
    /** 设备唯一编号。 */
    private LocalDateTime lastHeartbeatTms;
    /** 最后一次收到心跳的时间。 */
    private String status;
    /** 设备状态，当前默认记录为 NORMAL。 */
    private LocalDateTime updateTms;
    /** 记录最后更新时间。 */

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public LocalDateTime getLastHeartbeatTms() {
        return lastHeartbeatTms;
    }

    public void setLastHeartbeatTms(LocalDateTime lastHeartbeatTms) {
        this.lastHeartbeatTms = lastHeartbeatTms;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getUpdateTms() {
        return updateTms;
    }

    public void setUpdateTms(LocalDateTime updateTms) {
        this.updateTms = updateTms;
    }
}
