package com.chinasofti.huateng.model.app;

/**
 * IF8A-08 获取车站代码请求参数。
 */
public class RequestStationCodeListReqDTO {
    /** 线路代码；为空时返回全部线路车站。 */
    private String lineCode;

    public String getLineCode() { return lineCode; }
    public void setLineCode(String lineCode) { this.lineCode = lineCode; }
}
