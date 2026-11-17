package com.chinasofti.huateng.model.cardpool;

public class CardPoolReservationReqDTO {
    private String cardType;
    private String businessType;
    private String businessId;
    private String ownerId;


    public String getCardType() { return cardType; }


    public void setCardType(String cardType) { this.cardType = cardType; }


    public String getBusinessType() { return businessType; }


    public void setBusinessType(String businessType) { this.businessType = businessType; }


    public String getBusinessId() { return businessId; }


    public void setBusinessId(String businessId) { this.businessId = businessId; }


    public String getOwnerId() { return ownerId; }


    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
}
