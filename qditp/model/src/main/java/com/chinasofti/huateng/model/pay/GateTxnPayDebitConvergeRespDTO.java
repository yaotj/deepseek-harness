package com.chinasofti.huateng.model.pay;


public class GateTxnPayDebitConvergeRespDTO {

    private String retCode;

    private String retMsg;

    private String origOrderNo;


    private Boolean converged;


    private String debitStatus;

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

    public String getOrigOrderNo() {
        return origOrderNo;
    }

    public void setOrigOrderNo(String origOrderNo) {
        this.origOrderNo = origOrderNo;
    }

    public Boolean getConverged() {
        return converged;
    }

    public void setConverged(Boolean converged) {
        this.converged = converged;
    }

    public String getDebitStatus() {
        return debitStatus;
    }

    public void setDebitStatus(String debitStatus) {
        this.debitStatus = debitStatus;
    }

    @Override
    public String toString() {
        return "GateTxnPayDebitConvergeRespDTO{retCode='" + retCode + "', retMsg='" + retMsg
                + "', origOrderNo='" + origOrderNo + "', converged=" + converged
                + ", debitStatus='" + debitStatus + "'}";
    }
}
