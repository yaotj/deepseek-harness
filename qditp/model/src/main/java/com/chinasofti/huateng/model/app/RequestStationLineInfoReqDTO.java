package com.chinasofti.huateng.model.app;

/**
 * 查询车站线路信息请求参数。
 */
public class RequestStationLineInfoReqDTO {
    /** 车站代码。 */
    private String stationCode;

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }
}
