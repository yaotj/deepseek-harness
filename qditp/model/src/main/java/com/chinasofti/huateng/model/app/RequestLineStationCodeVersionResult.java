package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;


public class RequestLineStationCodeVersionResult extends CommonResult {
    private String lineCodeIdx;
    private String stationCodeIdx;
    private String changeDate;

    public String getLineCodeIdx() { return lineCodeIdx; }
    public void setLineCodeIdx(String lineCodeIdx) { this.lineCodeIdx = lineCodeIdx; }
    public String getStationCodeIdx() { return stationCodeIdx; }
    public void setStationCodeIdx(String stationCodeIdx) { this.stationCodeIdx = stationCodeIdx; }
    public String getChangeDate() { return changeDate; }
    public void setChangeDate(String changeDate) { this.changeDate = changeDate; }
}
