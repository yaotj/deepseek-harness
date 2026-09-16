package com.chinasofti.huateng.model.app;

/**
 * 支付域签约成功后，向账户域回写支付账号（{@code PAY_ACCOUNT_ID}）的入参。<b>域内接口，不是对外契约</b>。
 *
 * <p>2026-09-11（ADR-D32）新增。它补的是 ADR-D30 留下的覆盖率缺口：账户域
 * {@code APP_USER_PAY_CHANNEL.PAY_ACCOUNT_ID} 原先<b>只有 IF8A-77 会写</b>，
 * 因此用户签约后、走 IF8A-77 之前该列恒为空、运营页面显示 {@code -}。
 * 由支付域在签约落库提交后推一次，该列才在「签约那一刻」就完整。</p>
 *
 * <p><b>本 DTO 不经过 {@code parseBizData}</b>（调用方是 pay-sign-server 而非 APP），
 * 按 {@code docs/domain/README.md} 的分类判据属**对内接口**，加字段不受「对外契约 NEVER 加字段」约束。</p>
 *
 * <p><b>NEVER 用它去改 {@code USER_ITP_REG_INFO.THIRD_PAY_ID}</b>：那一列是 IF8A-77
 * 「更换默认支付方式」这个**业务动作**的产物，签约成功时用户尚未做出该选择，
 * 越过业务动作去写它等于替用户决定了默认支付方式。详见 {@code ADR-D32}。</p>
 */
public class SyncPayAccountIdReqDTO {

    /**
     * 签约流水号，对应支付域 {@code APP_PAY_SIGN_INFO.REQUEST_SIGN_SEQ}
     * 与账户域 {@code APP_USER_PAY_CHANNEL.REQ_CONTRACT_NO}。<b>这是两域唯一的共有键。</b>
     */
    private String reqContractNo;

    /**
     * 支付中心侧的支付账号（{@code payUserId}）。
     *
     * <p><b>与另两个同名概念 NEVER 混用</b>：{@code THIRD_PAY_ID} 是账户域自有、APP 上送；
     * {@code DISPLAY_ACCOUNT} 在支付域但由账户域推送；本字段是支付中心返回的。</p>
     */
    private String payAccountId;

    public String getReqContractNo() {
        return reqContractNo;
    }

    public void setReqContractNo(String reqContractNo) {
        this.reqContractNo = reqContractNo;
    }

    public String getPayAccountId() {
        return payAccountId;
    }

    public void setPayAccountId(String payAccountId) {
        this.payAccountId = payAccountId;
    }

    @Override
    public String toString() {
        return "SyncPayAccountIdReqDTO{reqContractNo='" + reqContractNo
                + "', payAccountId='" + payAccountId + "'}";
    }
}
