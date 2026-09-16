package com.chinasofti.huateng.model.app;

/**
 * 按签约流水号查询支付通道请求（account-server 内部只读接口）。
 *
 * <p>存在的唯一原因：{@code APP_PAY_SIGN_INFO.CARD_ID} / {@code CARD_TYPE} 在本项目里
 * <b>从来没有被写过（全库为 NULL）</b>，签约链路不落这两列；而 {@code APP_TERMINATION_REQUEST}
 * 的同名两列是 NOT NULL。因此 IF8A-75 走 {@code createTerminationRequest} 补建申请时，
 * 从签约记录回填必然拿到 NULL 并抛 ORA-01400（2026-09-08 实测）。
 * 票卡信息的真实来源是 account-server 的 {@code APP_USER_PAY_CHANNEL}，其
 * {@code REQ_CONTRACT_NO} 即签约流水号。</p>
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
