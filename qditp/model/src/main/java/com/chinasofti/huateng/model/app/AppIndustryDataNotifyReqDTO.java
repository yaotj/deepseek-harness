package com.chinasofti.huateng.model.app;


public class AppIndustryDataNotifyReqDTO {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String cardData;

    private String companionFlag;
    private String signType;
    private String sign;

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

    public String getCardData() {
        return cardData;
    }

    public void setCardData(String cardData) {
        this.cardData = cardData;
    }

    public String getCompanionFlag() {
        return companionFlag;
    }

    public void setCompanionFlag(String companionFlag) {
        this.companionFlag = companionFlag;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    @Override
    public String toString() {
        return "AppIndustryDataNotifyReqDTO{thirdUserId='" + thirdUserId + "', cardId='" + cardId
                + "', cardType='" + cardType + "', cardData='" + (cardData != null ? "[length=" + cardData.length() + "]" : null)
                + "', companionFlag='" + companionFlag
                + "', signType='" + signType + "', sign='" + (sign != null ? "[length=" + sign.length() + "]" : null) + "'}";
    }
}
