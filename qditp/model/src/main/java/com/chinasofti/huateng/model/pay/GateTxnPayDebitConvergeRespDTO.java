package com.chinasofti.huateng.model.pay;

/**
 * 补款收敛原过闸订单扣费状态的响应。
 */
public class GateTxnPayDebitConvergeRespDTO {

    /** 业务码，{@code 0000} 表示对端已处理完毕（不代表本次改了行。 */
    private String retCode;

    /** 业务文案，仅用于日志与工单。 */
    private String retMsg;

    /** 回显原过闸订单号，便于调用方在批量场景下对齐请求与响应。 */
    private String origOrderNo;

    /**
     * 本次调用是否真的把原订单推进到了 {@code SUCCESS}（UPDATE 影响行数 &gt; 0）。
     */
    private Boolean converged;

    /**
     * 处理后原订单的权威 {@code DEBIT_STATUS}（取自对端本次读到的值）。
     */
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
