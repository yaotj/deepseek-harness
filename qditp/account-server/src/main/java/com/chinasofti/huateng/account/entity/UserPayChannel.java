package com.chinasofti.huateng.account.entity;

import java.time.LocalDateTime;

public class UserPayChannel {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private String channel;
    private String thirdPayId;
    private String reqContractNo;
    private String status;
    private LocalDateTime createTms;
    private LocalDateTime updateTms;

    /**
     * 支付中心侧的付款账号标识（{@code PAY_ACCOUNT_ID}），2.0.63 新增。
     *
     * <p><b>与 {@link #thirdPayId} 不是同一个东西，NEVER 混用</b>：{@code THIRD_PAY_ID} 是 APP
     * 加通道时上送的第三方支付标识（本域自有）；本列的值来自**支付中心签约回调的 {@code payUserId}**，
     * 由支付域的 {@code APP_PAY_SIGN_INFO.PAY_ACCOUNT_ID} 同步而来。</p>
     *
     * <p>加这一列是为了把运营页面「支付账号」列的数据源从**逐渠道跨域 RPC** 改成本地读
     * （原先每行都要打一次 {@code paySignClient.querySignInfoBySeq}，见 ADR-D30）。
     * <b>当前只有 IF8A-77 会回写它</b>，签约成功时支付域不回调账户域，因此新签约的通道行在走过
     * IF8A-77 之前该列为 {@code null}、页面显示 {@code -}。</p>
     */
    private String payAccountId;

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

    public String getThirdPayId() {
        return thirdPayId;
    }

    public void setThirdPayId(String thirdPayId) {
        this.thirdPayId = thirdPayId;
    }

    public String getReqContractNo() {
        return reqContractNo;
    }

    public void setReqContractNo(String reqContractNo) {
        this.reqContractNo = reqContractNo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTms() {
        return createTms;
    }

    public void setCreateTms(LocalDateTime createTms) {
        this.createTms = createTms;
    }

    public LocalDateTime getUpdateTms() {
        return updateTms;
    }

    public void setUpdateTms(LocalDateTime updateTms) {
        this.updateTms = updateTms;
    }

    public String getPayAccountId() {
        return payAccountId;
    }

    public void setPayAccountId(String payAccountId) {
        this.payAccountId = payAccountId;
    }
}
