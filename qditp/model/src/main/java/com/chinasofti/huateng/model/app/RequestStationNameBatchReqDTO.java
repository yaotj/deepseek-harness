package com.chinasofti.huateng.model.app;

import java.util.List;


public class RequestStationNameBatchReqDTO {
    private List<String> stationCodes;

    public List<String> getStationCodes() {
        return stationCodes;
    }

    public void setStationCodes(List<String> stationCodes) {
        this.stationCodes = stationCodes;
    }

    @Override
    public String toString() {
        return "RequestStationNameBatchReqDTO{stationCodes=" + stationCodes + "}";
    }
}