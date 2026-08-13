package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行-解约执行结果。
 */
public class TerminationExecuteResult {

    /**
     * 协议号
     */
    private String agreementCode;

    /**
     * 返回码
     */
    private String retCode;

    /**
     * 返回消息
     */
    private String retMsg;

    /**
     * 当前解约状态：PENDING/COMPLETED/FAIL/TERMINATED
     */
    private String status;

    public String getAgreementCode() {
        return agreementCode;
    }

    public void setAgreementCode(String agreementCode) {
        this.agreementCode = agreementCode;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
