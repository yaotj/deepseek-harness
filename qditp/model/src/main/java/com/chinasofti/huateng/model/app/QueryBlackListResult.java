package com.chinasofti.huateng.model.app;


public class QueryBlackListResult {

    private String retCode;


    private String retMsg;


    private String inBlack;


    private Integer failedCount;

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

    public String getInBlack() {
        return inBlack;
    }

    public void setInBlack(String inBlack) {
        this.inBlack = inBlack;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }
}
