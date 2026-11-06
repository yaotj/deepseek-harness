package com.chinasofti.huateng.account.page;

/**
 * 非支付宝用户注册信息后台查询条件。
 *
 * <p>四个筛选字段之间是 <b>AND 组合</b>，空白（null 或全空格）视为未填、不参与谓词；
 * 调用方 MUST 保证至少有一个非空，否则查询会退化成全表扫描。
 */
public class ItpUserSearchQuery {
    private String msisdn;
    private String cardId;
    private String thirdUserId;
    /** 渠道签约流水号，对应 {@code USER_ITP_REG_INFO.REQ_CONTRACT_NO}。 */
    private String reqContractNo;
    /** 注册状态：{@code ACTIVE}=有效（{@code DEL_YN=1}），{@code CANCELED}=已注销（{@code DEL_YN=0}）。 */
    private String status;

    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getReqContractNo() { return reqContractNo; }
    public void setReqContractNo(String reqContractNo) { this.reqContractNo = reqContractNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
