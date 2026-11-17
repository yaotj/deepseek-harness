package com.chinasofti.huateng.model.app;


public class RequestExcessFareReqDTO {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String upgradeAreaType;
    private String upgradeStationCode;
    private String upgradeReason;
    private String upgradeDateTime;

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getUpgradeAreaType() { return upgradeAreaType; }
    public void setUpgradeAreaType(String upgradeAreaType) { this.upgradeAreaType = upgradeAreaType; }
    public String getUpgradeStationCode() { return upgradeStationCode; }
    public void setUpgradeStationCode(String upgradeStationCode) { this.upgradeStationCode = upgradeStationCode; }
    public String getUpgradeReason() { return upgradeReason; }
    public void setUpgradeReason(String upgradeReason) { this.upgradeReason = upgradeReason; }
    public String getUpgradeDateTime() { return upgradeDateTime; }
    public void setUpgradeDateTime(String upgradeDateTime) {
        this.upgradeDateTime = upgradeDateTime;
    }

    @Override
    public String toString() {
        return "RequestExcessFareReqDTO{thirdUserId='" + thirdUserId + "', cardId='" + cardId
                + "', cardType='" + cardType + "', upgradeAreaType='" + upgradeAreaType
                + "', upgradeStationCode='" + upgradeStationCode + "', upgradeReason='" + upgradeReason
                + "', upgradeDateTime='" + upgradeDateTime + "'}";
    }
}
