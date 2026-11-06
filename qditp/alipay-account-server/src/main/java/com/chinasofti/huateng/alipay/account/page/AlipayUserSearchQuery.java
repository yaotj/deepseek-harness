package com.chinasofti.huateng.alipay.account.page;

/**
 * 支付宝注册用户后台查询条件。
 *
 * <p>四个筛选字段之间是 <b>AND 组合</b>，空白（null 或全空格）视为未填、不参与谓词；
 * 调用方 MUST 保证至少有一个非空，否则查询会退化成全表扫描。
 */
public class AlipayUserSearchQuery {
    private String msisdn;
    private String cardId;
    private String thirdUserId;
    /** 渠道签约流水号，对应 {@code ALIPAY_USER_INFO.REQ_CONTRACT_NO}。 */
    private String reqContractNo;
    /** 页码，缺省由服务层按 1 兜底。 */
    private Integer pageNum;
    /** 每页条数，缺省由服务层按 10 兜底、上限 100。 */
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
