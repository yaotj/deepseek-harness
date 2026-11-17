package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;


public class AlipayTripReceiveCardDataReqDTO {


    private String thirdUserId;


    private String cardId;


    private String cardType;


    private String cardData;


    private String companionFlag;


    private String entryDeviceCode;


    private String exitDeviceCode;

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

    public String getEntryDeviceCode() {
        return entryDeviceCode;
    }

    public void setEntryDeviceCode(String entryDeviceCode) {
        this.entryDeviceCode = entryDeviceCode;
    }

    public String getExitDeviceCode() {
        return exitDeviceCode;
    }

    public void setExitDeviceCode(String exitDeviceCode) {
        this.exitDeviceCode = exitDeviceCode;
    }
}
