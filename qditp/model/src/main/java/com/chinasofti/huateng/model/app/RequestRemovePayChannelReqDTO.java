package com.chinasofti.huateng.model.app;

/**
 * 删除用户支付通道请求参数。
 */
public class RequestRemovePayChannelReqDTO {
    /** 三方用户 ID。 */
    private String thirdUserId;
    /** 卡 ID。 */
    private String cardId;
    /** 卡类型。 */
    private String cardType;
    /** 支付通道编码。 */
    private String channel;

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

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    @Override
    public String toString() {
        return "RequestRemovePayChannelReqDTO{thirdUserId='" + thirdUserId + "', cardId='" + cardId
                + "', cardType='" + cardType + "', channel='" + channel + "'}";
    }
}
