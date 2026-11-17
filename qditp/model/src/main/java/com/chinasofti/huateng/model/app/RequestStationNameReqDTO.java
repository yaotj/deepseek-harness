package com.chinasofti.huateng.model.app;


public class RequestStationNameReqDTO {
    private String stationCode;

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    @Override
    public String toString() {
        return "RequestStationNameReqDTO{stationCode='" + stationCode + "'}";
    }
}
