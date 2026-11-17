package com.chinasofti.huateng.model.app;

import java.util.List;


public class RequestStationNameBatchResult {
    private String retCode;
    private String retMsg;
    private List<RequestStationNameResult> stationNameList;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public List<RequestStationNameResult> getStationNameList() {
        return stationNameList;
    }

    public void setStationNameList(List<RequestStationNameResult> stationNameList) {
        this.stationNameList = stationNameList;
    }

    @Override
    public String toString() {
        return "RequestStationNameBatchResult{retCode='" + retCode + "', retMsg='" + retMsg +
                "', stationNameList=" + stationNameList + "}";
    }
}