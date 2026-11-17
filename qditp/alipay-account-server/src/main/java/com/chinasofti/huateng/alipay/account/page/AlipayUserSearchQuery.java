package com.chinasofti.huateng.alipay.account.page;


public class AlipayUserSearchQuery {
    private String msisdn;
    private String cardId;
    private String thirdUserId;
    private String reqContractNo;
    private Integer pageNum;
    private Integer pageSize;

    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getReqContractNo() { return reqContractNo; }
    public void setReqContractNo(String reqContractNo) { this.reqContractNo = reqContractNo; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
