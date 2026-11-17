package com.chinasofti.huateng.account.page;

import java.time.LocalDateTime;


public class ItpPayChannelView {

    private String thirdUserId;


    private String cardId;


    private String cardType;


    private String channel;


    private String thirdPayId;


    private String reqContractNo;


    private String payAccountId;


    private String status;


    private boolean defaultChannel;


    private boolean terminationReady;


    private LocalDateTime createTms;


    private LocalDateTime updateTms;

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getThirdPayId() { return thirdPayId; }
    public void setThirdPayId(String thirdPayId) { this.thirdPayId = thirdPayId; }
    public String getReqContractNo() { return reqContractNo; }
    public void setReqContractNo(String reqContractNo) { this.reqContractNo = reqContractNo; }
    public String getPayAccountId() { return payAccountId; }
    public void setPayAccountId(String payAccountId) { this.payAccountId = payAccountId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isDefaultChannel() { return defaultChannel; }
    public void setDefaultChannel(boolean defaultChannel) { this.defaultChannel = defaultChannel; }
    public boolean isTerminationReady() { return terminationReady; }
    public void setTerminationReady(boolean terminationReady) { this.terminationReady = terminationReady; }
    public LocalDateTime getCreateTms() { return createTms; }
    public void setCreateTms(LocalDateTime createTms) { this.createTms = createTms; }
    public LocalDateTime getUpdateTms() { return updateTms; }
    public void setUpdateTms(LocalDateTime updateTms) { this.updateTms = updateTms; }
}
