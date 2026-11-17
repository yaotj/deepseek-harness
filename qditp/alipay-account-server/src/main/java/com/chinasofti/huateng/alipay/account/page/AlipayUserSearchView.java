package com.chinasofti.huateng.alipay.account.page;

import java.time.LocalDateTime;


public class AlipayUserSearchView {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String msisdn;
    private String channel;
    private String thirdPayId;
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
