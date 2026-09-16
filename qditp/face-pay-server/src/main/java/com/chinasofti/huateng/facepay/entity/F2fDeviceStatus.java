package com.chinasofti.huateng.facepay.entity;

import java.time.LocalDateTime;

/**
 * 设备最新心跳状态（表 F2F_DEVICE_STATUS）。
 *
 * <p>字段与 face-pay-server/src/main/resources/sql/f2f-schema.sql 一一对应，
 * 改字段 MUST 同步改 DDL。
 *
 * <p>本表的两点特殊之处：
 * <ul>
 *   <li><b>一台设备只有一行</b>，主键是复合主键 {@code (CHANNEL, DEVICE_ID)}，没有自增 ID，
 *       因此实体里也没有 {@code id} 字段。</li>
 *   <li><b>只存最新状态不存历史。</b>规格要求设备每 1 分钟上报心跳，ITP 据此确认设备正常；
 *       心跳更新走 {@code MERGE INTO} 单语句 upsert，NEVER 先 SELECT 判存在再 insert/update。</li>
 * </ul>
 */
public class F2fDeviceStatus {

    /** 受理渠道，与 F2F_ORDER.CHANNEL 同口径：01-APP，02-TVM，03-BOM。复合主键第一列。 */
    private String channel;

    /** 设备号。复合主键第二列，与 CHANNEL 一起唯一定位一台设备。 */
    private String deviceId;

    /** 设备所在车站编码。 */
    private String stationCode;

    /** 最近一次心跳时间。IDX_F2F_DEVICE_HB 建在此列上，支撑超时扫表。 */
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
