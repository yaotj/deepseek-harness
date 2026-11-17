package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.ArrayList;
import java.util.List;


public class RequestLineCodeListResult extends CommonResult {
    private List<LineCodeRecordDTO> lineCodeRecord = new ArrayList<>();

    public List<LineCodeRecordDTO> getLineCodeRecord() { return lineCodeRecord; }
    public void setLineCodeRecord(List<LineCodeRecordDTO> lineCodeRecord) { this.lineCodeRecord = lineCodeRecord; }
}
