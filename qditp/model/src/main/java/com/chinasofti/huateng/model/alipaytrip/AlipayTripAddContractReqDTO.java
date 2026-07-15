package com.chinasofti.huateng.model.alipaytrip;

/**
 * 支付宝出行-添加签约信息请求参数。
 */
public class AlipayTripAddContractReqDTO {

    /**
     * 支付渠道
     */
    private String channel;

    /**
     * 用户ID
     */
    private String thirdUserId;

    /**
     * 签约协议号，系统生成的唯一协议编号
     */
    private String agreementCode;

    /**
     * 渠道协议号，第三方渠道的协议编号
     */
    private String channelAgreementCode;

    /**
     * 渠道用户账户，用户在第三方渠道的账户标识
     */
    private String channelUserAccount;

    /**
     * 发卡类型代码，如：0007
     */
    private String cardIssueCode;

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getAgreementCode() {
        return agreementCode;
    }

    public void setAgreementCode(String agreementCode) {
        this.agreementCode = agreementCode;
    }

    public String getChannelAgreementCode() {
        return channelAgreementCode;
    }

    public void setChannelAgreementCode(String channelAgreementCode) {
        this.channelAgreementCode = channelAgreementCode;
    }

    public String getChannelUserAccount() {
        return channelUserAccount;
    }

    public void setChannelUserAccount(String channelUserAccount) {
        this.channelUserAccount = channelUserAccount;
    }

    public String getCardIssueCode() {
        return cardIssueCode;
    }

    public void setCardIssueCode(String cardIssueCode) {
        this.cardIssueCode = cardIssueCode;
    }
}
