package com.chinasofti.huateng.model.app;


public class RequestUserAccInfoResult {

    private String retCode;

    private String retMsg;


    private int unpaidCount;

    private int failureCount;

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

    public int getUnpaidCount() {
        return unpaidCount;
    }

    public void setUnpaidCount(int unpaidCount) {
        this.unpaidCount = unpaidCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    @Override
    public String toString() {
        return "RequestUserAccInfoResult{retCode='" + retCode + "', retMsg='" + retMsg
                + "', unpaidCount=" + unpaidCount + ", failureCount=" + failureCount + '}';
    }
}
