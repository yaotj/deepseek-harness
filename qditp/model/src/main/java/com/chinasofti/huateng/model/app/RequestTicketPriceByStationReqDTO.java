package com.chinasofti.huateng.model.app;

/**
 * IF8A-10 计算票价请求参数。
 */
public class RequestTicketPriceByStationReqDTO {
    /** 进站车站代码。 */
    private String entryStationCode;
    /** 出站车站代码。 */
    private String exitStationCode;

    public String getEntryStationCode() { return entryStationCode; }
    public void setEntryStationCode(String entryStationCode) { this.entryStationCode = entryStationCode; }
    public String getExitStationCode() { return exitStationCode; }
    public void setExitStationCode(String exitStationCode) { this.exitStationCode = exitStationCode; }
}
