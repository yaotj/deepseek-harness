package com.chinasofti.huateng.model.app;

/**
 * IF8A-75 直接解绑支付方式应答（接口规范 §3.59）。
 */
public class UnbindAgreementResult {

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
        return "UnbindAgreementResult{retCode='" + retCode + "', retMsg='" + retMsg + "'}";
    }
}
