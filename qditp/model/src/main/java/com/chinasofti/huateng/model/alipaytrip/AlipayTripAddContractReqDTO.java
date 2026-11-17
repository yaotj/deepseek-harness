package com.chinasofti.huateng.model.alipaytrip;


public class AlipayTripAddContractReqDTO {


    private String channel;


    private String thirdUserId;


    private String agreementCode;


    private String channelAgreementCode;


    private String channelUserAccount;


    private String cardIssueCode;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getAgreementCode() {
        return agreementCode;
    }

    public void setAgreementCode(String agreementCode) {
        this.agreementCode = agreementCode;
    }

    public String getChannelAgreementCode() {
        return channelAgreementCode;
    }

    public void setChannelAgreementCode(String channelAgreementCode) {
        this.channelAgreementCode = channelAgreementCode;
    }

    public String getChannelUserAccount() {
        return channelUserAccount;
    }

    public void setChannelUserAccount(String channelUserAccount) {
        this.channelUserAccount = channelUserAccount;
    }

    public String getCardIssueCode() {
        return cardIssueCode;
    }

    public void setCardIssueCode(String cardIssueCode) {
        this.cardIssueCode = cardIssueCode;
    }
}
