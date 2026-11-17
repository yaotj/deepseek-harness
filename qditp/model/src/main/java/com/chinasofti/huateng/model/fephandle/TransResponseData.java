package com.chinasofti.huateng.model.fephandle;

import java.io.Serializable;

public class TransResponseData implements Serializable {

    private static final long serialVersionUID = -3719143895386880848L;

    private String transDataType;
    private String devNodeId;
    private String txnSnType;
    private String txnTime;
    private String devTxnSn;
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
