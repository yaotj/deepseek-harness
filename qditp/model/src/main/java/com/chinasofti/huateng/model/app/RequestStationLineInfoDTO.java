package com.chinasofti.huateng.model.app;


public class RequestStationLineInfoDTO {
    private String stationCode;
    private String stationName;
    private String lineCode;
    private String lineName;

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getStationName() {
        return stationName;
    }

    public void setStationName(String stationName) {
        this.stationName = stationName;
    }

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public String getLineName() {
        return lineName;
    }

    public void setLineName(String lineName) {
        this.lineName = lineName;
    }

    @Override
    public String toString() {
        return "RequestStationLineInfoDTO{stationCode='" + stationCode + "', stationName='" + stationName
                + "', lineCode='" + lineCode + "', lineName='" + lineName + "'}";
    }
}
