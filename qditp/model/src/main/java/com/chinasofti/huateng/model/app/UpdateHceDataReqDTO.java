package com.chinasofti.huateng.model.app;


public class UpdateHceDataReqDTO {

    private String cardId;


    private String hceData;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getHceData() {
        return hceData;
    }

    public void setHceData(String hceData) {
        this.hceData = hceData;
    }

    @Override
    public String toString() {
        return "UpdateHceDataReqDTO{cardId='" + cardId + "', hceData='" + hceData + "'}";
    }
}
