package com.chinasofti.huateng.model.app;

/**
 * IF8A-75 直接解绑支付方式请求（接口规范 §3.59）。
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
