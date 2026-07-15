package com.chinasofti.huateng.model.app;

/**
 * IF8A-04 请求自助补站请求参数。
 */
public class RequestExcessFareReqDTO {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    /** 01：补进站 02：补出站 */
    private String upgradeAreaType;
    private String upgradeStationCode;
    private String upgradeReason;
    /** YYYYMMDDHHmmss */
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
    public void setUpgradeDateTime(String upgradeDateTime) { this.upgradeDateTime = upgradeDateTime; }
}
