package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * 查询车站名称响应参数。
 */
public class RequestStationNameResult extends CommonResult {
    /** 车站代码。 */
    private String stationCode;
    /** 车站中文名称。 */
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
