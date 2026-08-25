package com.chinasofti.huateng.model.app;

/**
 * 车站线路信息 DTO，用于 Mapper 映射车站名称及所属线路信息。
 */
public class RequestStationLineInfoDTO {
    /** 车站代码。 */
    private String stationCode;
    /** 车站中文名称。 */
    private String stationName;
    /** 所属线路代码。 */
    private String lineCode;
    /** 线路中文名称。 */
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
