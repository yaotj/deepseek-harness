package com.chinasofti.huateng.model.alipaytrip;

import com.chinasofti.huateng.common.response.CommonResult;

/**
 * 支付宝出行-行业数据推送请求参数。
 * 与APP行业数据通知参数相同。
 */
public class AlipayTripReceiveCardDataReqDTO {

    /**
     * 第三方用户ID
     */
    private String thirdUserId;

    /**
     * 卡片ID/逻辑卡号
     */
    private String cardId;

    /**
     * 卡片类型
     */
    private String cardType;

    /**
     * 卡数据HexString
     */
    private String cardData;

    /**
     * 同行票标识
     */
    private String companionFlag;

    /**
     * 进站设备码
     */
    private String entryDeviceCode;

    /**
     * 出站设备码
     */
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
