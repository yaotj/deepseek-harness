package com.chinasofti.huateng.key.page;

/**
 * 综管台「设备密钥同步」页展示对象（只读）。
 *
 * <p>源表 COM_DEVICE_SYN_KEY 按 DEVICE_ID 做 MERGE，因此本页展示的是每台设备的
 * <strong>最新</strong>密钥同步状态，不是历史流水。</p>
 */
public class DeviceKeySyncView {

    /** 设备编号（AGM 设备号）。 */
    private String deviceId;

    /** 车站编码（deviceId 前 4 位）。 */
    private String stationCode;

    /** 同步状态：SUCCESS / FAIL。 */
    private String status;

    /** 响应码。 */
    private String respRetCode;

    /** 响应消息。 */
    private String respRetMsg;

    /** 返回密钥版本数量。 */
    private Integer respKeyVersionCount;

    /** 处理耗时（毫秒）。 */
    private Long processDurationMs;

    /** 最近同步请求时间（yyyy-MM-dd HH:mm:ss）。 */
    private String reqSynKeyDate;

    /** 首次注册时间（yyyy-MM-dd HH:mm:ss）。 */
    private String regDate;

    /** 设备上报的当前密钥版本列表（CLOB 原文，详情查看）。 */
    private String agmKeyCurverList;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRespRetCode() {
        return respRetCode;
    }

    public void setRespRetCode(String respRetCode) {
        this.respRetCode = respRetCode;
    }

    public String getRespRetMsg() {
        return respRetMsg;
    }

    public void setRespRetMsg(String respRetMsg) {
        this.respRetMsg = respRetMsg;
    }

    public Integer getRespKeyVersionCount() {
        return respKeyVersionCount;
    }

    public void setRespKeyVersionCount(Integer respKeyVersionCount) {
        this.respKeyVersionCount = respKeyVersionCount;
    }

    public Long getProcessDurationMs() {
        return processDurationMs;
    }

    public void setProcessDurationMs(Long processDurationMs) {
        this.processDurationMs = processDurationMs;
    }

    public String getReqSynKeyDate() {
        return reqSynKeyDate;
    }

    public void setReqSynKeyDate(String reqSynKeyDate) {
        this.reqSynKeyDate = reqSynKeyDate;
    }

    public String getRegDate() {
        return regDate;
    }

    public void setRegDate(String regDate) {
        this.regDate = regDate;
    }

    public String getAgmKeyCurverList() {
        return agmKeyCurverList;
    }

    public void setAgmKeyCurverList(String agmKeyCurverList) {
        this.agmKeyCurverList = agmKeyCurverList;
    }
}
