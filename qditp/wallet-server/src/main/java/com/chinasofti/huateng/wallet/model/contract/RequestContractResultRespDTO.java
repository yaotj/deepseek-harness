package com.chinasofti.huateng.wallet.model.contract;

/**
 * IF8A-22 签约结果咨询响应报文。
 */
public class RequestContractResultRespDTO {
    /**
     * 返回码。
     */
    private String retCode;

    /**
     * 返回消息。
     */
    private String retMsg;

    /**
     * 签约状态，NOT_SIGNED/SIGNED/UNSIGNED。
     */
    private String status;

    /**
     * 支付用户编码。
     */
    private String payAccountId;

    /**
     * 支付渠道签约流水号。
     */
    private String payAgreementNo;

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

    public String getPayAccountId() {
        return payAccountId;
    }

    public void setPayAccountId(String payAccountId) {
        this.payAccountId = payAccountId;
    }

    public String getPayAgreementNo() {
        return payAgreementNo;
    }

    public void setPayAgreementNo(String payAgreementNo) {
        this.payAgreementNo = payAgreementNo;
    }
}
