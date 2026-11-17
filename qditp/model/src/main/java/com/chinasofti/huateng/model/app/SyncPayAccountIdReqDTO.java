package com.chinasofti.huateng.model.app;


public class SyncPayAccountIdReqDTO {


    private String reqContractNo;


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
