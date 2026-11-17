package com.chinasofti.huateng.model.ticket;


public class RequestCardDataAnalyseReqDTO {

    private String providerId;
    private String msisdn;
    private String cardId;
    private String updateType;


    private String managerCode;


    private String bomStationCode;

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getMsisdn() {
        return msisdn;
    }

    public void setMsisdn(String msisdn) {
        this.msisdn = msisdn;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getUpdateType() {
        return updateType;
    }

    public void setUpdateType(String updateType) {
        this.updateType = updateType;
    }

    public String getManagerCode() {
        return managerCode;
    }

    public void setManagerCode(String managerCode) {
        this.managerCode = managerCode;
    }

    public String getBomStationCode() {
        return bomStationCode;
    }

    public void setBomStationCode(String bomStationCode) {
        this.bomStationCode = bomStationCode;
    }
}
