package com.chinasofti.huateng.model.app;


public class StationCodeRecordDTO {
    private String lineCode;
    private String stationCode;
    private String stationNameZH;
    private String stationNameEN;
    private String transferYn;

    public String getLineCode() { return lineCode; }
    public void setLineCode(String lineCode) { this.lineCode = lineCode; }
    public String getStationCode() { return stationCode; }
    public void setStationCode(String stationCode) { this.stationCode = stationCode; }
    public String getStationNameZH() { return stationNameZH; }
    public void setStationNameZH(String stationNameZH) { this.stationNameZH = stationNameZH; }
    public String getStationNameEN() { return stationNameEN; }
    public void setStationNameEN(String stationNameEN) { this.stationNameEN = stationNameEN; }
    public String getTransferYn() { return transferYn; }
    public void setTransferYn(String transferYn) {
        this.transferYn = transferYn;
    }

    @Override
    public String toString() {
        return "StationCodeRecordDTO{lineCode='" + lineCode + "', stationCode='" + stationCode + "', stationNameZH='" + stationNameZH + "', stationNameEN='" + stationNameEN + "', transferYn='" + transferYn + "'}";
    }
}
