package com.chinasofti.huateng.key.entity;

/**
 * The latest AGM key-version report received from a device.
 */
public class ComDeviceSynKey {
    private String deviceId;
    private String agmKeyCurverList;
    private String stationCode;
    private String respRetCode;
    private String respRetMsg;
    private Integer respKeyVersionCount;
    private Long processDurationMs;
    private String status;
    private String errorMsg;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getAgmKeyCurverList() { return agmKeyCurverList; }
    public void setAgmKeyCurverList(String agmKeyCurverList) { this.agmKeyCurverList = agmKeyCurverList; }
    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getRespRetCode() { return respRetCode; }
    public void setRespRetCode(String respRetCode) { this.respRetCode = respRetCode; }
    public String getRespRetMsg() { return respRetMsg; }
    public void setRespRetMsg(String respRetMsg) { this.respRetMsg = respRetMsg; }
    public Integer getRespKeyVersionCount() { return respKeyVersionCount; }
    public void setRespKeyVersionCount(Integer respKeyVersionCount) { this.respKeyVersionCount = respKeyVersionCount; }
    public Long getProcessDurationMs() { return processDurationMs; }
    public void setProcessDurationMs(Long processDurationMs) { this.processDurationMs = processDurationMs; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
}
