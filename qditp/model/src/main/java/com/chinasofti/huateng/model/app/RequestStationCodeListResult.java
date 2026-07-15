package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.ArrayList;
import java.util.List;

/**
 * IF8A-08 获取车站代码响应参数。
 */
public class RequestStationCodeListResult extends CommonResult {
    /** 车站代码记录列表。 */
    private List<StationCodeRecordDTO> stationCodeRecord = new ArrayList<>();

    public List<StationCodeRecordDTO> getStationCodeRecord() { return stationCodeRecord; }
    public void setStationCodeRecord(List<StationCodeRecordDTO> stationCodeRecord) { this.stationCodeRecord = stationCodeRecord; }
}
