package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行-获取行业数据请求参数。
 */
public class AlipayTripRequestIndustryDataReqDTO {

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
}
