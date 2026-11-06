package com.chinasofti.huateng.alipay.account.page;

import java.time.LocalDateTime;

/**
 * 支付宝注册用户后台展示对象：手机号与支付用户标识脱敏，签约流水号按原值返回
 * （它不是账号类信息，运营需要拿它与渠道侧对单，「签约渠道」页同样按原值展示）。
 */
public class AlipayUserSearchView {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String msisdn;
    private String channel;
    private String thirdPayId;
    /** 渠道签约流水号（{@code REQ_CONTRACT_NO}）。 */
    private String reqContractNo;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getMsisdn() { return msisdn; }
    public void setMsisdn(String msisdn) { this.msisdn = msisdn; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getThirdPayId() { return thirdPayId; }
    public void setThirdPayId(String thirdPayId) { this.thirdPayId = thirdPayId; }
    public String getReqContractNo() { return reqContractNo; }
    public void setReqContractNo(String reqContractNo) { this.reqContractNo = reqContractNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
