package com.chinasofti.huateng.model.app;


public class UserCancelResult {

    private String retCode;

    private String retMsg;

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

    @Override
    public String toString() {
        return "UserCancelResult{retCode='" + retCode + "', retMsg='" + retMsg + "'}";
    }
}
