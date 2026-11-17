package com.chinasofti.huateng.model.app;


public class RequestTransStatisticsResult {
    private String retCode;
    private String retMsg;
    private TripDataDTO tripData;

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

    public TripDataDTO getTripData() {
        return tripData;
    }

    public void setTripData(TripDataDTO tripData) {
        this.tripData = tripData;
    }
}
