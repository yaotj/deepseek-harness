package com.chinasofti.huateng.model.fephandle;

public class RegisterResponseData {

    private String registerDataType;
    private String devNodeId;
    private String createDatetime;
    private String status;

    // Getters and Setters

    public String getRegisterDataType() {
        return registerDataType;
    }

    public void setRegisterDataType(String registerDataType) {
        this.registerDataType = registerDataType;
    }

    public String getDevNodeId() {
        return devNodeId;
    }

    public void setDevNodeId(String devNodeId) {
        this.devNodeId = devNodeId;
    }

    public String getCreateDatetime() {
        return createDatetime;
    }

    public void setCreateDatetime(String createDatetime) {
        this.createDatetime = createDatetime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
