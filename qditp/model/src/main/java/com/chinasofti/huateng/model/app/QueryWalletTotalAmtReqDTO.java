package com.chinasofti.huateng.model.app;

/** 钱包累计金额查询请求，对应 ITP-App /app/queryTotalAmt。金额单位为分。 */
public class QueryWalletTotalAmtReqDTO {
    private String thirdUserId;
    private String cardType;
    private String msisdn;
    private String extend1;
    private String extend2;
    private String cardIssueCode;

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
    public String getExtend1() { return extend1; }
    public void setExtend1(String extend1) { this.extend1 = extend1; }
    public String getExtend2() { return extend2; }
    public void setExtend2(String extend2) { this.extend2 = extend2; }
    public String getCardIssueCode() { return cardIssueCode; }
    public void setCardIssueCode(String cardIssueCode) { this.cardIssueCode = cardIssueCode; }
}
