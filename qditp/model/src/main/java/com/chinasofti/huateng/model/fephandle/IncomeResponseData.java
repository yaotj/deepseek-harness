package com.chinasofti.huateng.model.fephandle;

import java.io.Serial;
import java.io.Serializable;

public class IncomeResponseData implements Serializable {
    @Serial
    private static final long serialVersionUID = 5401277793960612087L;

    // 设备ID，长度8
    private String devNodeId;
    // 收益事件码，长度2
    private String eventCode;
    // 收益事件时间，长度14
    private String devTxnTime;
    // 事务数据流水号，长度10
    private String devTxnSn;
    // 处理状态 长度2
    private String status;

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
