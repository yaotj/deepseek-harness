package com.chinasofti.huateng.model.fephandle;

import java.io.Serial;
import java.io.Serializable;

public class TransRequestData implements Serializable {
    @Serial
    private static final long serialVersionUID = 4570363585558121821L;

    private String transDataType;
    private String devNodeId;
    private String txnSnType;
    private String txnTime;
    private String devTxnSn;
    private Object transData;

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

    public Object getTransData() {
        return transData;
    }

    public void setTransData(Object transData) {
        this.transData = transData;
    }
}
