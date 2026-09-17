package com.chinasofti.huateng.model.app;

/**
 * 黑名单「可解除性」盘点结果的单条明细。
 */
public class BlacklistReleaseCandidateDTO {

    private String cardId;

    /** 三方用户 ID，可能为空（拉黑入口不强制上送）。 */
    private String thirdUserId;

    private String reason;

    /** 拉黑时间，格式 yyyy-MM-dd HH:mm:ss。 */
    private String createTime;

    /** 闸机出站扣费（GATE_TXN_PAY）是否仍有未结清订单。查询失败时为 null。 */
    private Boolean gateUnsettled;

    /** 支付宝出行（ALIPAY_PAY_LOG）是否仍有未结清订单。查询失败时为 null。 */
    private Boolean alipayUnsettled;

    /**
     * 盘点结论。
     */
    private String settleStatus;

    /** 查询失败时的。 */
    private String failReason;

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public Boolean getGateUnsettled() {
        return gateUnsettled;
    }

    public void setGateUnsettled(Boolean gateUnsettled) {
        this.gateUnsettled = gateUnsettled;
    }

    public Boolean getAlipayUnsettled() {
        return alipayUnsettled;
    }

    public void setAlipayUnsettled(Boolean alipayUnsettled) {
        this.alipayUnsettled = alipayUnsettled;
    }

    public String getSettleStatus() {
        return settleStatus;
    }

    public void setSettleStatus(String settleStatus) {
        this.settleStatus = settleStatus;
    }

    public String getFailReason() {
        return failReason;
    }

    public void setFailReason(String failReason) {
        this.failReason = failReason;
    }
}
