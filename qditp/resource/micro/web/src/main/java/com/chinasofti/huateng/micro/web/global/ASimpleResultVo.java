package com.chinasofti.huateng.micro.web.global;

import java.io.Serializable;

public class ASimpleResultVo<T> implements Serializable {

    public static final String NET_WORK_ERR = "-1";

    private String retCode;
    private String retMsg;
    private T data;

    public ASimpleResultVo() {
    }


    public boolean checkNetWorkErr() {
        if (retCode != null && retCode.equals(NET_WORK_ERR)) {
            return true;
        }
        return false;
    }


    public boolean checkNotMapping() {
        if (retCode == null && data == null && retMsg == null) {
            return true;
        }
        return false;
    }

    public ASimpleResultVo(String retCode) {
        this.retCode = retCode;
    }

    public ASimpleResultVo(T data) {
        this.data = data;
    }

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "ASimpleResultVo{" +
                "retCode='" + retCode + '\'' +
                ", retMsg='" + retMsg + '\'' +
                ", data=" + data +
                '}';
    }
}
