package com.chinasofti.huateng.model.app;


public class RequestStationCodeListReqDTO {
    private String lineCode;

    public String getLineCode() { return lineCode; }
    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    @Override
    public String toString() {
        return "RequestStationCodeListReqDTO{lineCode='" + lineCode + "'}";
    }
}
