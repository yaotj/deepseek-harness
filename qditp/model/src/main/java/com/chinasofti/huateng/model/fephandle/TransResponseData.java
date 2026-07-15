package com.chinasofti.huateng.model.fephandle;

import java.io.Serializable;

public class TransResponseData implements Serializable {

    private static final long serialVersionUID = -3719143895386880848L;

    // 交易数据类型，长度4
    private String transDataType;
    // 设备ID，长度8
    private String devNodeId;
    // 流水号类型，长度4
    private String txnSnType;
    // 交易时间，长度14
    private String txnTime;
    // 终端设备流水号，长度10
    private String devTxnSn;
    // 处理状态 长度2
    private String status;

    public String getTransDataType() {
        return transDataType;
    }

    public void setTransDataType(String transDataType) {
        this.transDataType = transDataType;
    }

    public String getDevNodeId() {
        return devNodeId;
    }

    public void setDevNodeId(String devNodeId) {
        this.devNodeId = devNodeId;
    }

    public String getTxnSnType() {
        return txnSnType;
    }

    public void setTxnSnType(String txnSnType) {
        this.txnSnType = txnSnType;
    }

    public String getTxnTime() {
        return txnTime;
    }

    public void setTxnTime(String txnTime) {
        this.txnTime = txnTime;
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
