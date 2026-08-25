package com.chinasofti.huateng.model.app;

import java.util.List;

/**
 * 批量查询车站名称响应参数。
 */
public class RequestStationNameBatchResult {
    /** 接口处理状态码，0000 表示成功。 */
    private String retCode;
    /** 返回信息。 */
    private String retMsg;
    /** 车站名称列表。 */
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