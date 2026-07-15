package com.chinasofti.huateng.model.app;

/**
 * 删除用户支付通道请求参数。
 *
 * <p>解约成功后由 pay-sign-server 调用 account-server 使用。
 * account-server 会先删除 APP_USER_PAY_CHANNEL 中的通道记录；
 * 如果该通道正好是 User_ITP_Reg_Info 中的默认支付通道，则同步清空注册信息中的默认通道字段。</p>
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
}
