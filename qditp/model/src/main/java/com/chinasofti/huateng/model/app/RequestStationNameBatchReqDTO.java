package com.chinasofti.huateng.model.app;

import java.util.List;

/**
 * 批量查询车站名称请求参数。
 */
public class RequestStationNameBatchReqDTO {
    /** 车站代码列表。 */
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