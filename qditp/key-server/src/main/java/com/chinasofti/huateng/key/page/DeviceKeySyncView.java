package com.chinasofti.huateng.key.page;


public class DeviceKeySyncView {

    private String deviceId;

    private String stationCode;

    private String status;

    private String respRetCode;

    private String respRetMsg;

    private Integer respKeyVersionCount;

    private Long processDurationMs;

    private String reqSynKeyDate;

    private String regDate;

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
