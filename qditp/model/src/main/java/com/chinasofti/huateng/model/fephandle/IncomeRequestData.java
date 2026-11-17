package com.chinasofti.huateng.model.fephandle;

import java.io.Serial;
import java.io.Serializable;

public class IncomeRequestData implements Serializable {
    @Serial
    private static final long serialVersionUID = 8923036286016852243L;

    private String devNodeId;
    private String eventCode;
    private String devTxnTime;
    private String devTxnSn;
    private Object incomeData;

    public String getDevNodeId() {
        return devNodeId;
    }

    public void setDevNodeId(String devNodeId) {
        this.devNodeId = devNodeId;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getDevTxnTime() {
        return devTxnTime;
    }

    public void setDevTxnTime(String devTxnTime) {
        this.devTxnTime = devTxnTime;
    }

    public String getDevTxnSn() {
        return devTxnSn;
    }

    public void setDevTxnSn(String devTxnSn) {
        this.devTxnSn = devTxnSn;
    }

    public Object getIncomeData() {
        return incomeData;
    }

    public void setIncomeData(Object incomeData) {
        this.incomeData = incomeData;
    }
}
