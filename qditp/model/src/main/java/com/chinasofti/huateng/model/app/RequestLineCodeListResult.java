package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

import java.util.ArrayList;
import java.util.List;

/**
 * IF8A-07 获取线路代码响应参数。
 */
public class RequestLineCodeListResult extends CommonResult {
    /** 线路代码记录列表。 */
    private List<LineCodeRecordDTO> lineCodeRecord = new ArrayList<>();

    public List<LineCodeRecordDTO> getLineCodeRecord() { return lineCodeRecord; }
    public void setLineCodeRecord(List<LineCodeRecordDTO> lineCodeRecord) { this.lineCodeRecord = lineCodeRecord; }
}
