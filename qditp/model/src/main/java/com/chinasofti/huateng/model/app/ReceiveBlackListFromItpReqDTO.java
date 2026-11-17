package com.chinasofti.huateng.model.app;


public class ReceiveBlackListFromItpReqDTO {


    private String thirdUserId;


    private String cardId;


    private String cardType;


    private String blackListType;


    private String optionDate;


    private String expireTime;


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

    public String getBlackListType() {
        return blackListType;
    }

    public void setBlackListType(String blackListType) {
        this.blackListType = blackListType;
    }

    public String getOptionDate() {
        return optionDate;
    }

    public void setOptionDate(String optionDate) {
        this.optionDate = optionDate;
    }

    public String getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(String expireTime) {
        this.expireTime = expireTime;
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
        return "ReceiveBlackListFromItpReqDTO{thirdUserId='" + thirdUserId + "', cardId='" + cardId
                + "', cardType='" + cardType + "', blackListType='" + blackListType
                + "', optionDate='" + optionDate + "', expireTime='" + expireTime
                + "', signType='" + signType + "', sign='" + (sign != null ? "[length=" + sign.length() + "]" : null) + "'}";
    }
}
