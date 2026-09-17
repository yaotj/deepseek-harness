package com.chinasofti.huateng.model.app;

/**
 * 支付域签约成功后，向账户域回写支付账号（{@code PAY_ACCOUNT_ID}）的入参。域内接口，不是对外契约。
 */
public class SyncPayAccountIdReqDTO {

    /**
     * 签约流水号，对应支付域 {@code APP_PAY_SIGN_INFO.REQUEST_SIGN_SEQ}
     */
    private String reqContractNo;

    /**
     * 支付中心侧的支付账号（{@code payUserId}）。
     */
    private String payAccountId;

    public String getReqContractNo() {
        return reqContractNo;
    }

    public void setReqContractNo(String reqContractNo) {
        this.reqContractNo = reqContractNo;
    }

    public String getPayAccountId() {
        return payAccountId;
    }

    public void setPayAccountId(String payAccountId) {
        this.payAccountId = payAccountId;
    }

    @Override
    public String toString() {
        return "SyncPayAccountIdReqDTO{reqContractNo='" + reqContractNo
                + "', payAccountId='" + payAccountId + "'}";
    }
}
