package com.chinasofti.huateng.model.app;

/**
 * 黑名单「可解除性」盘点结果的单条明细。
 *
 * <p>只用于只读盘点报表，**NEVER** 被任何删除逻辑消费。当前阶段的定时任务只输出这份明细供人工核对，
 * 不执行解除：{@code BLACKLIST} 表没有拉黑类型字段，`REASON` 是四个来源混写的自由文本
 * （支付中心应答原文 / 代码拼接 / 外部接口传入 / 运营手工输入），生产实测 35 条 ADD 里
 * 22 条是「用户挂失补卡」——与欠费无关，误删等于让挂失旧卡恢复过闸。
 * 因此判定「该不该解除」MUST 由人看 {@code reason} 决定，代码只负责把欠费事实查清楚。</p>
 */
public class BlacklistReleaseCandidateDTO {

    /** 卡号。 */
    private String cardId;

    /** 三方用户 ID，可能为空（拉黑入口不强制上送）。 */
    private String thirdUserId;

    /** 拉黑原因原文，人工据此判断是否属于欠费类。 */
    private String reason;

    /** 拉黑时间，格式 yyyy-MM-dd HH:mm:ss。 */
    private String createTime;

    /** 闸机出站扣费（GATE_TXN_PAY）是否仍有未结清订单。查询失败时为 null。 */
    private Boolean gateUnsettled;

    /** 支付宝出行（ALIPAY_PAY_LOG）是否仍有未结清订单。查询失败时为 null。 */
    private Boolean alipayUnsettled;

    /**
     * 盘点结论。
     *
     * <p>{@code SETTLED} 两个欠费源都查成功且都无欠费；{@code UNSETTLED} 至少一个源仍有欠费；
     * {@code UNKNOWN} 至少一个源查询失败，事实不明。</p>
     *
     * <p>{@code SETTLED} 只代表「钱结清了」，**NEVER** 等同于「可以解除」——挂失补卡类记录也会是
     * SETTLED。</p>
     */
    private String settleStatus;

    /** 查询失败时的原因说明，成功时为 null。 */
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
