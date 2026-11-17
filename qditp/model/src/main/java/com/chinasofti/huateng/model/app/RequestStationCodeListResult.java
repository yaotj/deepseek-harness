package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.ArrayList;
import java.util.List;


public class RequestStationCodeListResult extends CommonResult {
    private List<StationCodeRecordDTO> stationCodeRecord = new ArrayList<>();

    public List<StationCodeRecordDTO> getStationCodeRecord() { return stationCodeRecord; }
    public void setStationCodeRecord(List<StationCodeRecordDTO> stationCodeRecord) { this.stationCodeRecord = stationCodeRecord; }
}
