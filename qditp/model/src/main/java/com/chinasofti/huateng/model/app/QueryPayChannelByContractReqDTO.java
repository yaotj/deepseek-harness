package com.chinasofti.huateng.model.app;


public class QueryPayChannelByContractReqDTO {

    private String reqContractNo;

    public String getReqContractNo() {
        return reqContractNo;
    }

    public void setReqContractNo(String reqContractNo) {
        this.reqContractNo = reqContractNo;
    }

    @Override
    public String toString() {
        return "QueryPayChannelByContractReqDTO{reqContractNo='" + reqContractNo + "'}";
    }
}
