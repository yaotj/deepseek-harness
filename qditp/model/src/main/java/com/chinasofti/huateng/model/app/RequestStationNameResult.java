package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;


public class RequestStationNameResult extends CommonResult {
    private String stationCode;
    private String stationName;

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
}
