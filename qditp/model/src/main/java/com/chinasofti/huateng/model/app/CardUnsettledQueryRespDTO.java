package com.chinasofti.huateng.model.app;


public class CardUnsettledQueryRespDTO {

    private String resultCode;

    private String resultMsg;

    private boolean hasUnsettled;

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultMsg() {
        return resultMsg;
    }

    public void setResultMsg(String resultMsg) {
        this.resultMsg = resultMsg;
    }

    public boolean isHasUnsettled() {
        return hasUnsettled;
    }

    public void setHasUnsettled(boolean hasUnsettled) {
        this.hasUnsettled = hasUnsettled;
    }
}
