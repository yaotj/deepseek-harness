package com.chinasofti.huateng.model.app;


public class UnbindAgreementReqDTO {

    private String thirdUserId;

    private String paymentVendor;

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
