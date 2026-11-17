package com.chinasofti.huateng.model.app;


public class RequestStationLineInfoReqDTO {
    private String stationCode;

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    @Override
    public String toString() {
        return "RequestStationLineInfoReqDTO{stationCode='" + stationCode + "'}";
    }
}
