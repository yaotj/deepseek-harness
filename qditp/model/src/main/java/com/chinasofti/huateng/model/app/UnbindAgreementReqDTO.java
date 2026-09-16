package com.chinasofti.huateng.model.app;

/**
 * IF8A-75 直接解绑支付方式请求（接口规范 §3.59）。
 *
 * <p>与 IF8A-36 请求解约的区别：IF8A-36 只登记申请、置解约中，真正向支付渠道发起要等账期结束由
 * web-admin 的 Quartz 任务扫表；<b>本接口用于「用户长时间未登录需强制解绑」，立即向支付渠道发起解绑</b>。</p>
 *
 * <p>三个字段是 {@code ExecuteTerminationReqDTO} 的子集。<b>票卡信息不能从签约记录回填</b>：
 * {@code APP_PAY_SIGN_INFO.CARD_ID} / {@code CARD_TYPE} 在本项目全库为 NULL（签约链路不写这两列），
 * 而 {@code APP_TERMINATION_REQUEST} 的同名列是 NOT NULL。pay-sign 侧改为用 {@code requestSignSeq}
 * 经 {@code AccountClient.queryPayChannelByContractNo} 反查 {@code APP_USER_PAY_CHANNEL}
 * （其 {@code REQ_CONTRACT_NO} 即签约流水号）取得，因此本接口仍不需要 APP 传票卡信息。</p>
 *
 * <p><b>钱包（paymentVendor=0B）不适用本接口</b>：钱包开户后直接绑定支付通道、不生成签约流水，
 * 没有可解约的协议，解绑 MUST 走 IF8A-25 {@code requestRemovePayChannel}。</p>
 */
public class UnbindAgreementReqDTO {

    /** 用户 id。 */
    private String thirdUserId;

    /** 支付渠道，取值与 {@code USER_ITP_REG_INFO.CHANNEL} 同一套编码（如 03 支付宝）。 */
    private String paymentVendor;

    /** 签约流水号，对应 {@code APP_TERMINATION_REQUEST.REQUEST_SIGN_SEQ}，是本接口的定位主键。 */
    private String requestSignSeq;

    public String getThirdUserId() {
        return thirdUserId;
    }

    public void setThirdUserId(String thirdUserId) {
        this.thirdUserId = thirdUserId;
    }

    public String getPaymentVendor() {
        return paymentVendor;
    }

    public void setPaymentVendor(String paymentVendor) {
        this.paymentVendor = paymentVendor;
    }

    public String getRequestSignSeq() {
        return requestSignSeq;
    }

    public void setRequestSignSeq(String requestSignSeq) {
        this.requestSignSeq = requestSignSeq;
    }

    @Override
    public String toString() {
        return "UnbindAgreementReqDTO{thirdUserId='" + thirdUserId
                + "', paymentVendor='" + paymentVendor
                + "', requestSignSeq='" + requestSignSeq + "'}";
    }
}
