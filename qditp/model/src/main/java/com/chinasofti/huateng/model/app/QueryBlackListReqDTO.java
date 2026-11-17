package com.chinasofti.huateng.model.app;


public class QueryBlackListReqDTO {

    private String cardId;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    @Override
    public String toString() {
        return "QueryBlackListReqDTO{cardId='" + cardId + "'}";
    }
}
