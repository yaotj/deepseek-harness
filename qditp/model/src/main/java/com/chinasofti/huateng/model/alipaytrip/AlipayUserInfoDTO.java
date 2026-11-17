package com.chinasofti.huateng.model.alipaytrip;


public class AlipayUserInfoDTO {


    private String thirdUserId;


    private String cardId;


    private String cardType;


    private String thirdPayId;


    private String reqContractNo;


    private String channel;


    private String phone;

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

    public String getThirdPayId() {
        return thirdPayId;
    }

    public void setThirdPayId(String thirdPayId) {
        this.thirdPayId = thirdPayId;
    }

    public String getReqContractNo() {
        return reqContractNo;
    }

    public void setReqContractNo(String reqContractNo) {
        this.reqContractNo = reqContractNo;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
