package com.chinasofti.huateng.model.app;


public class BlacklistReleaseCandidateDTO {

    private String cardId;

    private String thirdUserId;

    private String reason;

    private String createTime;

    private Boolean gateUnsettled;

    private Boolean alipayUnsettled;


    private String settleStatus;

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
