package com.chinasofti.huateng.model.app;

import java.util.List;


public class RequestTransStatisticsReqDTO {
    private String cardId;
    private String startDate;
    private String endDate;
    private String thirdUserId;
    private String cardType;
    private List<String> cardIdList;
    private List<String> cardTypeList;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getCardType() {
        return cardType;
    }

    public void setCardType(String cardType) {
        this.cardType = cardType;
    }

    public List<String> getCardIdList() {
        return cardIdList;
    }

    public void setCardIdList(List<String> cardIdList) {
        this.cardIdList = cardIdList;
    }

    public List<String> getCardTypeList() {
        return cardTypeList;
    }

    public void setCardTypeList(List<String> cardTypeList) {
        this.cardTypeList = cardTypeList;
    }

    @Override
    public String toString() {
        return "RequestTransStatisticsReqDTO{cardId='" + cardId
                + "', startDate='" + startDate + "', endDate='" + endDate
                + "', thirdUserId='" + thirdUserId + "', cardType='" + cardType
                + "', cardTypeList=" + cardTypeList + "}";
    }
}
