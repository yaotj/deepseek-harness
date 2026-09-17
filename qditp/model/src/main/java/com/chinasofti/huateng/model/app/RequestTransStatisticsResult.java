package com.chinasofti.huateng.model.app;

/**
 * IF8A-41 查询账单统计应答。
 */
public class RequestTransStatisticsResult {
    /** 返回码。 */
    private String retCode;
    /** 返回消息。 */
    private String retMsg;
    /** 统计数据对象。 */
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
