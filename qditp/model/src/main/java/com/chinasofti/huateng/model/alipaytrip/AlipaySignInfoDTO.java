package com.chinasofti.huateng.model.alipaytrip;


public class AlipaySignInfoDTO {


    private String thirdUserId;


    private String channelAgreementCode;


    private String reqContractNo;


    private String cardId;


    private String cardType;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getChannelAgreementCode() {
        return channelAgreementCode;
    }

    public void setChannelAgreementCode(String channelAgreementCode) {
        this.channelAgreementCode = channelAgreementCode;
    }

    public String getReqContractNo() {
        return reqContractNo;
    }

    public void setReqContractNo(String reqContractNo) {
        this.reqContractNo = reqContractNo;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }
}
