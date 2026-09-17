package com.chinasofti.huateng.model.app;

/**
 * 按签约流水号查询支付通道请求（account-server 内部只读接口）。
 */
public class QueryPayChannelByContractReqDTO {

    /** 签约流水号，对应 {@code APP_USER_PAY_CHANNEL.REQ_CONTRACT_NO}。 */
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
