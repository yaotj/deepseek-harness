package com.chinasofti.huateng.model.app;

/**
 * IF8A-02 请求同步密钥请求参数。
 */
public class RequestKeyListReqDTO {
    /**
     * 第三方用户ID，由 APP 侧传入，后续会转换为四字节HEX作为 keyUserId。
     */
    private String thirdUserId;

    /**
     * 地铁会员卡号，也作为生成用户 SM2 密钥时的逻辑卡号基础数据。
     */
    private String cardId;

    /**
     * 卡类型编码：02 表示二维码后付费单程票，03/04 表示 HCE 卡。
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

    @Override
    public String toString() {
        return "RequestKeyListReqDTO{thirdUserId='" + thirdUserId + "', cardId='" + cardId + "', cardType='" + cardType + "'}";
    }
}
