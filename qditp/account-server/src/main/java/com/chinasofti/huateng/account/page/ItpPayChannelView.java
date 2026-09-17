package com.chinasofti.huateng.account.page;

import java.time.LocalDateTime;

/**
 * ITP 用户当前票种的支付渠道运营展示对象。
 */
public class ItpPayChannelView {
    /**
     * 第三方用户标识。
     */
    private String thirdUserId;

    /**
     * 逻辑卡号。
     */
    private String cardId;

    /**
     * 票种（本域口径的 CARD_TYPE）。
     */
    private String cardType;

    /**
     * 支付渠道代码。
     */
    private String channel;

    /**
     * 渠道侧支付账号标识。
     */
    private String thirdPayId;

    /**
     * 签约流水号，跨域定位签约记录用。
     */
    private String reqContractNo;

    /**
     * 支付账户标识。
     */
    private String payAccountId;

    /**
     * 支付域返回的签约状态，取不到时为空。
     */
    private String status;

    /**
     * 是否本用户本票种的默认渠道。
     */
    private boolean defaultChannel;

    /**
     * 支付域判定当前是否可发起解约，仅供页面按钮置灰。
     */
    private boolean terminationReady;

    /**
     * 记录创建时间。
     */
    private LocalDateTime createTms;

    /**
     * 记录更新时间。
     */
    private LocalDateTime updateTms;

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getThirdPayId() { return thirdPayId; }
    public void setThirdPayId(String thirdPayId) { this.thirdPayId = thirdPayId; }
    public String getReqContractNo() { return reqContractNo; }
    public void setReqContractNo(String reqContractNo) { this.reqContractNo = reqContractNo; }
    public String getPayAccountId() { return payAccountId; }
    public void setPayAccountId(String payAccountId) { this.payAccountId = payAccountId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isDefaultChannel() { return defaultChannel; }
    public void setDefaultChannel(boolean defaultChannel) { this.defaultChannel = defaultChannel; }
    public boolean isTerminationReady() { return terminationReady; }
    public void setTerminationReady(boolean terminationReady) { this.terminationReady = terminationReady; }
    public LocalDateTime getCreateTms() { return createTms; }
    public void setCreateTms(LocalDateTime createTms) { this.createTms = createTms; }
    public LocalDateTime getUpdateTms() { return updateTms; }
    public void setUpdateTms(LocalDateTime updateTms) { this.updateTms = updateTms; }
}
